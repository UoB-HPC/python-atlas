package uob_hpc.python_atlas

import cats.data.NonEmptyList
import cats.syntax.all.*
import cats.{Monoid, Order}
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.nio.DefaultAttribute
import sttp.model.Uri
import uob_hpc.python_atlas.Conda.{ChannelData, RepoData}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import scala.collection.immutable.{ArraySeq, TreeMap}
import scala.collection.parallel.CollectionConverters.*
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import scala.util.Try

def timed[R](block: => R): (R, Double) = {
  val t0     = System.nanoTime()
  val result = block
  val t1     = System.nanoTime()
  result -> ((t1 - t0).toDouble / 1e9)
}

def timed[R](name: String)(block: => R): R = {
  val (r, elapsed) = timed(block)
  println(s"[$name] ${elapsed}s")
  r
}

@main def main2: Unit = {
  println("Hey!")

  val xs = PyPi.Pep681.RepoIndex()

  xs.foreach { r =>

    var l = new AtomicLong()
    r.get.projects.foreach { p =>
//      println(s"DO! ${p.name}")
      val name = PyPi.Warehouse.Package(p.name)

    if (name.isLeft) {
      println(s"FAIL $p => $name")
    }
    if (name.right.get.isDefined) {

      if (name.right.get.get.info.requires_dist.isDefined) {
        l.getAndIncrement()
//          println(s"${p.name} DIST = ${name.right.get.get.info.requires_dist.get.size}")
      }

//        println(s"YES! ${p.name}")
    } else {
//        println(s"NO! ${p.name}")
    }

    }
    println(s"Done ${l.get()}")

//    IO.write(Paths.get("./names.json"))(r.projects.map(_.name).map(s => s""""$s"""").mkString("[", ", ", "]"))
//    IO.write(Paths.get("./names.txt"))(r.projects.map(_.name).mkString( "\n" ))

  }
  println(xs.map(_.get.projects.size))

}

case class JvmAtlasLayout(
    channels: ArraySeq[String],
    subdirs: ArraySeq[String],
    markers: ArraySeq[String],
    nodes: ArraySeq[JvmAtlasEntry],
    maxX: Double,
    maxY: Double
) extends AtlasLayout[ArraySeq, JvmAtlasEntry]

case class JvmAtlasEntry(
    name: String,

    // Layout
    position: (Double, Double),
    size: (Double, Double),

    // Edges
    dependents: Int,
    dependencyIndices: Set[Int],
    channelIndices: Set[Int],
    subdirIndices: Set[Int],
    markerIndices: Set[Int],

    //Meta
    description: Option[String],
    url: Option[String],
    licence: Option[String],
    version: Option[String],
    modifiedEpochMs: Option[Long],
) extends AtlasLayout.Entry[Set, (Double, Double), (Double, Double)]

private val mirror = summon[scala.deriving.Mirror.Of[JvmAtlasEntry]]
given Pickler.Writer[JvmAtlasEntry] =
  Pickler
    .writer[mirror.MirroredElemTypes]
    .comap[JvmAtlasEntry](Tuple.fromProductTyped(_))

import uob_hpc.python_atlas.Pickler.given

given Pickler.Writer[JvmAtlasLayout] = Pickler.macroW

//    def count(name: String, wit: Set[String] = Set.empty): Set[String] =
//      if (wit.contains(name)) Set.empty
//      else {
//        needPackages.get(name) match {
//          case None => wit + name
//          case Some(p) =>
//            val xs = p.depends.map(_.name)
//            val ys = wit + name
//            xs.flatMap(count(_, ys)).toSet
//        }
//      }

def createAtlas(channels: List[String], dotFile: Path, jsonFile: Path) = for {
  channelData: Seq[(Channel, ChannelData)] <- timed("Read ChannelData")(
    channels.par
      .map(c =>
        timed(s"Read ChannelData($c)")(ChannelData(c))
          .flatMap(_.toRight(new RuntimeException(s"No ChannelData available for $c")))
          .map(Channel(c) -> _)
      )
      .seq
      .sequence
  )

  latestPackageMeta = timed("Extract latest meta") {
    channelData
      .foldMap { case (_, chan) => chan.packages.map { case (name, p) => name -> NonEmptyList.one(p) } }
      .map { case (name, xs) => name -> xs.sortBy(_.timestamp.getOrElse(Instant.MIN))(using Order.fromOrdering[Instant]).head }
      .map { case (name, p : Conda.ChannelData.Package) =>
        val url = (p.dev_url.toList ::: p.doc_url.toList ::: p.home.toList).distinct
        .sortBy(s => !(s.startsWith("https://github.com") || s.startsWith("http://github.com")))
        .headOption
      val desc    = (p.description.toList ::: p.summary.toList).sortBy(_.length).headOption
      val licence = p.license
      val version = p.version
      name -> (desc, url, licence, version , p.timestamp.map(_.toEpochMilli))
    }
  }



  repos <- timed("Read RepoData")(
    (for {
      (chan, data) <- channelData
      subdir       <- data.subdirs // we don't need anything more from the repo
    } yield chan -> Subdir(subdir)).par
      .map { case k @ (chan -> subdir) =>
        timed(s"Read RepoData($chan, $subdir)")(RepoData(chan.name, subdir.name))
          .map(_.map(k -> _))
      }
      .seq
      .sequence
  )
  repoPackages: Map[(Channel, Subdir), Map[String, RepoData.Package]] = timed("Extract Packages")(
    repos.flatten
      .map { case k -> repo =>
        val latestPackages =
          repo.packages.values
            .groupMapReduce(_.name)(identity)((l, r) => if (l.build_number > r.build_number) l else r)
        k -> latestPackages
      }
      .filter(_._2.nonEmpty)
      .toMap
  )

  showKV = { (k: String, v: Any) => println(s"${s"$k:".padTo(25, ' ')}$v") }

  baseNames = channelData.flatMap(_._2.packages.keySet).toSet
  _         = showKV("Channel packages", baseNames.size)
  _ = repoPackages.foreach { case (k, v) =>
    // XXX package dependencies can refer to stuff outside of base, we show these as spills
    val namesWithDeps = v.keySet ++ v.values.flatMap(_.depends.map(_.name)).toSet
    showKV(s"  $k", s"${v.size} (spill=${(namesWithDeps -- baseNames)})")
    showKV(
      s"    (Base | $k)",
      s"${(baseNames -- v.keySet).size} ${(baseNames & v.keySet).size} ${(v.keySet -- baseNames).size}"
    )
  }

  markerPackages = List(
    List("r-base")                        -> "R language",
    List("python", "python_abi")          -> "Python",
    List("anaconda", "_anaconda_depends") -> "Anaconda",
    List("vc")                            -> "Visual Studio"
  )
  nameToMarker = markerPackages.flatMap { case (ks, v) => ks.map(_ -> v) }.to(TreeMap)

  graphWithoutMarkers = timed("Build graph") {
    import org.jgrapht.Graph
    import org.jgrapht.graph.DefaultDirectedGraph

    val G = new DefaultDirectedGraph[String, DefaultEdge](classOf)
    for {
      (_, ps) <- repoPackages
      (_, p)  <- ps
      if !nameToMarker.contains(p.name)
      ds = p.depends.map(_.name).toSet
    } {
      G.addVertex(p.name)
      ds.filterNot(nameToMarker.contains(_)).foreach { d =>
        G.addVertex(d)
        G.addEdge(d, p.name)
      }
    }
    G
  }

  _ = timed("Export graph") {
    import org.jgrapht.nio.dot.*

    import java.io.{StringWriter, Writer}
    val outgoingMax =
      graphWithoutMarkers.vertexSet.asScala.map(graphWithoutMarkers.outgoingEdgesOf(_).size).maxOption.getOrElse(0)
    val exporter = new DOTExporter[String, DefaultEdge](v => s""""$v"""")
    exporter.setGraphAttributeProvider { () =>
      Map(
        "layout"      -> DefaultAttribute.createAttribute("sfdp"),
        "outputorder" -> DefaultAttribute.createAttribute("edgesfirst"),
        "overlap"     -> DefaultAttribute.createAttribute("prism100"),
        "splines"     -> DefaultAttribute.createAttribute(false)
      ).asJava
    }

    exporter.setVertexAttributeProvider { v =>
      val dependencies = graphWithoutMarkers.incomingEdgesOf(v).size
      val dependent    = graphWithoutMarkers.outgoingEdgesOf(v).size
      val size         = linearScale(dependent, 0, outgoingMax, 1, 320)
      val radius       = math.sqrt(size / math.Pi)
      Map(
        "width"    -> DefaultAttribute.createAttribute(radius),
        "height"   -> DefaultAttribute.createAttribute(radius),
        "fontsize" -> DefaultAttribute.createAttribute(10: Integer)
      ).asJava
    }
    exporter.setEdgeAttributeProvider { e =>
      Map(
        "color" -> DefaultAttribute.createAttribute("#00000033"),
        "style" -> DefaultAttribute.createAttribute("invis")
      ).asJava
    }
    val writer = new StringWriter
    exporter.exportGraph(graphWithoutMarkers, writer)
    val dot = writer.toString
    IO.write(dotFile)(dot)
    println(s"Dot: ${Files.size(dotFile) / 1024}KiB")
  }

  channels = repoPackages.keys.map(_._1.name).to(ArraySeq).sorted
  subdirs  = repoPackages.keys.map(_._2.name).to(ArraySeq).sorted
  markers  = nameToMarker.values.to(ArraySeq).distinct.sorted
  dotJson <- timed("DOT")(Dot(dotFile))
  dot     <- timed("Parse DOT json0")(Dot.Json0(dotJson.mkString))
  nodeLayouts <- timed("Extract node layout") {
    // XXX arrow points toward the head node from each tail node (head <- tail)
    inline def trunc3(x: Double) = (x * 1000.0).floor / 1000.0
    inline def err(str: String)  = new RuntimeException(str)
    val nodeIdToIndexed          = dot.objects.map(_._gvid).zipWithIndex.toMap
    val nodeIdToIndexedHeads     = dot.edges.groupMapReduce(_.head)(x => Set(nodeIdToIndexed(x.tail)))(_ ++ _)
    val nodeIdToIndexedTails     = dot.edges.groupMapReduce(_.tail)(x => Set(nodeIdToIndexed(x.head)))(_ ++ _)

    val channelToIndexed = channels.zipWithIndex.toMap
    val subdirToIndexed  = subdirs.zipWithIndex.toMap
    val markerToIndexed  = markers.zipWithIndex.toMap
    val nodeGroups = repoPackages.toList.foldMap { case (chan, subdir) -> ps =>
      val namesWithoutMarkers =
        (ps.keySet ++ ps.values.flatMap(_.depends.map(_.name)).toSet).filterNot(nameToMarker.contains(_))
      val chans   = Set(channelToIndexed(chan.name))
      val subdirs = Set(subdirToIndexed(subdir.name))
      namesWithoutMarkers.map { n =>
        n ->
          (chans, subdirs, graphWithoutMarkers
            .incomingEdgesOf(n)
            .asScala
            .flatMap(e => nameToMarker.get(graphWithoutMarkers.getEdgeSource(e)).map(markerToIndexed(_)))
            .toSet)
      }.toMap
    }

    dot.objects.traverse { case (n: Dot.Json0.MetaNode) =>
      for {
        rawPos    <- n.data.get("pos").toRight(err("pos missing"))
        rawWidth  <- n.data.get("width").toRight(err("width missing"))
        rawHeight <- n.data.get("height").toRight(err("height missing"))
        width     <- rawWidth.toDoubleOption.toRight(err(s"Cannot parse width `$rawWidth` for $n"))
        height    <- rawHeight.toDoubleOption.toRight(err(s"Cannot parse height `$rawHeight` for $n"))
        pos2 <- rawPos.split(',').toList match {
          case rawX :: rawY :: Nil =>
            for {
              x <- rawX.toDoubleOption.toRight(err(s"Cannot parse x pos `$rawX` for $n"))
              y <- rawY.toDoubleOption.toRight(err(s"Cannot parse y pos `$rawY` for $n"))
            } yield trunc3(x) -> trunc3(y)
          case bad => Left(err(s"Cannot parse pos ($bad) for $n"))
        }
        (channels, subdirs, markers) = nodeGroups(n.name)
        (d, u, l, v, e ) = latestPackageMeta.getOrElse(n.name, (None, None, None, None, None) )
      } yield JvmAtlasEntry(
        name = n.name,
        position = pos2,
        size = (trunc3(width), trunc3(height)),
        dependents = nodeIdToIndexedHeads.get(n._gvid).map(_.size).getOrElse(0),
        dependencyIndices = nodeIdToIndexedTails.getOrElse(n._gvid, Set.empty),
        channelIndices = channels,
        subdirIndices = subdirs,
        markerIndices = markers,

        description = d,
        url = u,
        licence = l,
        version = v,
        modifiedEpochMs = e

      )
    }
  }
  maxX = nodeLayouts.view.map(_._2._1).maxOption.getOrElse(0d)
  maxY = nodeLayouts.view.map(_._2._2).maxOption.getOrElse(0d)

  layout = JvmAtlasLayout(channels, subdirs, markers, nodeLayouts, maxX, maxY)

  _ = layout.nodes.foreach(n => assert(n._5.forall(_ < layout.nodes.size)))
  _ = IO.write(jsonFile)(Pickler.write(layout))

} yield ()

val outputRoot = Paths.get("webapp/src/main/js/public")

@main def main(): Unit =
  timed("Total")(for {
    _ <- timed("Atlas")(
      createAtlas(
        channels = List("conda-forge", "anaconda"),
        dotFile = outputRoot.resolve(s"atlas.dot"),
        jsonFile = outputRoot.resolve("atlas_layout.json")
      )
    )

    maybeIndex <- timed("PEP681")(PyPi.Pep681.RepoIndex())
    index      <- maybeIndex.toRight(new RuntimeException("No PEP681 index available"))
    _         = println(s"PRP681 packages:${index.projects.size}")
    _         = println(s"PRP681 meta:    ${index.meta}")
    indexJson = Pickler.write(index.projects.map(_.name))
    _         = IO.write(outputRoot.resolve("pypi_names.json"))(indexJson)
  } yield ()).fold(throw _, _ => println("Done"))
