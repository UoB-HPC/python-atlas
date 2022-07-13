package uob_hpc.python_atlas

import cats.syntax.all.*
import uob_hpc.python_atlas.Pickler.{given, *}

import java.nio.file.Path
import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ArrayBuffer

object Dot {

  def apply(dotFile: Path, format: String = "json0"): Either[Throwable, String] = Either
    .catchNonFatal {
      import scala.sys.process.*
      val stderr = ArrayBuffer.empty[String]
      val stdout = s"dot -T$format ${dotFile.toAbsolutePath}"
        .lazyLines_!(ProcessLogger(fout = _ => (), ferr = l => stderr += l))
        .mkString
      stdout -> stderr
    }
    .flatMap {
      case (_, stderr) if stderr.nonEmpty =>
        Left(new RuntimeException(s"Dot reported error:\n${stderr.mkString("\n")}"))
      case (out, _) => Right(out)
    }

  // https://graphviz.org/docs/outputs/json/
  case class Json0(
      // required
      name: String,
      directed: Boolean,
      strict: Boolean,
      _subgraph_cnt: Int,
      objects: ArraySeq[Json0.MetaNode],
      edges: ArraySeq[Json0.MetaEdge],
      // extra
      data: Map[String, String]
  )
  object Json0 {

    case class MetaNode(_gvid: Int, name: String, data: Map[String, String])
    case class MetaEdge(_gvid: Int, head: Int, tail: Int, data: Map[String, String])

    private def peel(m: Map[String, ujson.Value], key: String) = m(key) -> (m - key)
    private def flatten(m: Map[String, ujson.Value]) = m.map { case (k, v) =>
      v match {
        case ujson.Obj(_) => k -> v.render()
        case _            => k -> v.value.toString
      }
    }

    given Pickler.Reader[Json0] = Pickler.reader[ujson.Obj].map[Json0] { o =>
      val map0                  = o.value.toMap
      val (name, map1)          = peel(map0, "name")
      val (directed, map2)      = peel(map1, "directed")
      val (strict, map3)        = peel(map2, "strict")
      val (_subgraph_cnt, map4) = peel(map3, "_subgraph_cnt")
      val (objects, map5)       = peel(map4, "objects")
      val (edges, map6)         = peel(map5, "edges")
      Json0(
        name.str,
        directed.bool,
        strict.bool,
        _subgraph_cnt.num.toInt,
        Pickler.read[ArraySeq[MetaNode]](objects),
        Pickler.read[ArraySeq[MetaEdge]](edges),
        flatten(map6)
      )
    }

    given Pickler.Reader[MetaNode] = Pickler.reader[ujson.Obj].map[MetaNode] { o =>
      val map0         = o.value.toMap
      val (id, map1)   = peel(map0, "_gvid")
      val (name, map2) = peel(map1, "name")
      MetaNode(id.num.toInt, name.str, flatten(map2))
    }

    given Pickler.Reader[MetaEdge] = Pickler.reader[ujson.Obj].map[MetaEdge] { o =>
      val map0         = o.value.toMap
      val (id, map1)   = peel(map0, "_gvid")
      val (head, map2) = peel(map1, "head")
      val (tail, map3) = peel(map2, "tail")
      MetaEdge(id.num.toInt, head.num.toInt, tail.num.toInt, flatten(map3))
    }

    def apply(json: String): Either[Throwable, Json0] = Either.catchNonFatal(Pickler.read[Json0](json))

  }
}
