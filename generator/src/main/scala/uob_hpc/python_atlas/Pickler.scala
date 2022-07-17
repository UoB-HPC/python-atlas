package uob_hpc.python_atlas

import java.time.Instant
object Pickler extends upickle.AttributeTagged {

  override implicit def OptionWriter[T: Writer]: Writer[Option[T]] =
    implicitly[Writer[T]].comap[Option[T]] {
      case None    => null.asInstanceOf[T]
      case Some(x) => x
    }

  override implicit def OptionReader[T: Reader]: Reader[Option[T]] =
    new Reader.Delegate[Any, Option[T]](implicitly[Reader[T]].map(Some(_))) {
      override def visitNull(index: Int) = None
    }

  opaque type StringList = List[String]
  object StringList {
    extension (xs : StringList){
      def toList : List[String] = xs
    }
    val empty: StringList = List.empty[String]
    given ReadWriter[StringList] =
      readwriter[ujson.Value]
        .bimap[StringList](
          xs => ujson.Arr(xs.map(ujson.Str(_))),
          {
            case ujson.Str(value) => value :: Nil
            case ujson.Arr(value) => value.map(_.str).toList
            case ujson.Null       => Nil
            case bad              => throw new RuntimeException(s"Bad type ${bad}")
          }
        )
  }

  given ReadWriter[Instant] = readwriter[Long].bimap[Instant](_.toEpochMilli, Instant.ofEpochMilli(_))

}
