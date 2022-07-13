package uob_hpc.python_atlas

import cats.syntax.all.*
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

def mkBoxChart(name: String)(xs: List[(String, Double)]) = {
  import org.nspl.*
  import org.nspl.awtrenderer.*

  val p = par()

  val plot = xyplot(
    (
      xs.map(_._2).zipWithIndex.map { case (x, i) => x -> i.toDouble },
      bar(
        horizontal = true,
        fill = RedBlue(0, 5),
        fillCol = 0
      ) :: Nil,
      NotInLegend
    )
  )(
    p.copy(
      yNumTicks = 0,
      yHeight = (xs.length * 1).fts,
      yLabFontSize = math.min(0.6d, p.yHeight.value / xs.length).fts,
      ynames = xs
        .map(_._1)
        .zipWithIndex
        .map(x => x._1 -> x._2.toDouble)
        .map(_.swap)
    )
  )

  Files.write(
    Paths.get(s"${name}.png").toAbsolutePath,
    renderToByteArray(plot.build, width = 1200),
    StandardOpenOption.CREATE,
    StandardOpenOption.TRUNCATE_EXISTING,
    StandardOpenOption.WRITE
  )
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
    position: (Double, Double),
    size: (Double, Double),
    dependents: Int,
    dependencyIndices: Set[Int],
    channelIndices: Set[Int],
    subdirIndices: Set[Int],
    markerIndices: Set[Int]
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
  channelData <- timed("Read ChannelData")(
    channels.par
      .map(c =>
        timed(s"Read ChannelData($c)")(ChannelData(c))
          .flatMap(_.toRight(new RuntimeException(s"No ChannelData available for $c")))
          .map(Channel(c) -> _)
      )
      .seq
      .sequence
  )
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
    List("vc")                            -> "Visual Studio",
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

  (incoming, outgoing) = graphWithoutMarkers.vertexSet.asScala.map { v =>
    val outgoingN = graphWithoutMarkers.outgoingEdgesOf(v).size
    val incomingN = graphWithoutMarkers.incomingEdgesOf(v).size
    (s"$v (${incomingN})"   -> incomingN.toDouble) ->
      (s"$v (${outgoingN})" -> outgoingN.toDouble)
  }.unzip
  _ = mkBoxChart("dependents")(outgoing.toList.sortBy(-_._2).take(50).reverse)
  _ = mkBoxChart("dependencies")(incoming.toList.sortBy(-_._2).take(50).reverse)

  channels = repoPackages.keys.map(_._1.name).to(ArraySeq).sorted
  subdirs  = repoPackages.keys.map(_._2.name).to(ArraySeq).sorted
  markers  = nameToMarker.values.to(ArraySeq).sorted
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
      } yield JvmAtlasEntry(
        name = n.name,
        position = pos2,
        size = (trunc3(width), trunc3(height)),
        dependents = nodeIdToIndexedHeads.get(n._gvid).map(_.size).getOrElse(0),
        dependencyIndices = nodeIdToIndexedTails.getOrElse(n._gvid, Set.empty),
        channelIndices = channels,
        subdirIndices = subdirs,
        markerIndices = markers
      )
    }
  }
  maxX = nodeLayouts.view.map(_._2._1).maxOption.getOrElse(0d)
  maxY = nodeLayouts.view.map(_._2._2).maxOption.getOrElse(0d)

  layout = JvmAtlasLayout(channels, subdirs, markers, nodeLayouts, maxX, maxY)

  _ = layout.nodes.foreach(n => assert(n._5.forall(_ < layout.nodes.size)))
  _ = IO.write(jsonFile)(Pickler.write(layout))

} yield ()

@main def main(): Unit =
  timed("Total")(for {
    _ <- timed("Atlas")(
      createAtlas(
        channels = List("conda-forge", "anaconda"),
        dotFile = Paths.get(s"atlas.dot"),
        jsonFile = Paths.get("atlas_layout.json")
      )
    )
    maybeIndex <- timed("PEP681")(PyPi.Pep681.RepoIndex())
    index      <- maybeIndex.toRight(new RuntimeException("No PEP681 index available"))
    _         = println(s"PRP681 packages:${index.projects.size}")
    _         = println(s"PRP681 meta:    ${index.meta}")
    indexJson = Pickler.write(index.projects.map(_.name))
    _         = IO.write(Paths.get("pypi_names.json"))(indexJson)
  } yield ()).fold(throw _, _ => println("Done"))
