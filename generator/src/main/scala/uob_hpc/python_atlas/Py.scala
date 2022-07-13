package uob_hpc.python_atlas

import cats.data.NonEmptyList

import scala.reflect.ClassTag

object Py {

  object RequirementParsers {

    import cats.parse.Rfc5234.*
    import cats.parse.{Parser as P, Parser0 as P0}

    enum VerSpec  {
      case Older, SameOrOlder, IsNot, Is, Newer, SameOrNewer, Compat, StringIs
    }

    extension (s: String) {
      def p: P[Unit] = P.string(s)
    }
    extension (c: Char) {
      def p: P[Char] = P.charIn(c)
    }
    extension [A](r: P[A]) {
      def * : P0[List[A]]        = r.rep0
      def + : P[NonEmptyList[A]] = r.rep
    }

    object PEP440 {
      // https://peps.python.org/pep-0440/#version-specifiers

      import VerSpec.*

      val letterOrDigit = digit | alpha
      val version_cmp = wsp.*.with1 *>
        ("<=".p.as(SameOrOlder) |
          "<".p.as(Older) |
          "!=".p.as(IsNot) |
          "===".p.as(StringIs) |
          "==".p.as(Is) |
          ">=".p.as(SameOrNewer) |
          ">".p.as(Newer) |
          "~=".p.as(Compat))
      val version      = (letterOrDigit | P.charIn("-_.*+!")).+.string
      val version_one  = (version_cmp ~ version) <* wsp.*
      val version_many = version_one.repSep(','.p)
      val versionspec  = version_many.between('('.p, ')'.p) | version_many

      val identifier_end = (
        PEP440.letterOrDigit |
          (P.charIn("-_.").*.with1 ~ PEP440.letterOrDigit)
      ).string
      val identifier = (PEP440.letterOrDigit ~ identifier_end.*).string

    }

  }

  // https://peps.python.org/pep-0508/
  case class PEP508Dependency(
      name: String,
      extras: List[String],
      versionSpec: List[(RequirementParsers.VerSpec, String)],
      marker: Option[String]
  )
  object PEP508Dependency {

    import RequirementParsers.*
    import cats.parse.Parser as P
    import cats.parse.Rfc5234.*

    private val urlspec = '@'.p ~ wsp.* *> P.until(sp)
    private val extras  = PEP440.identifier.repSep(','.p.surroundedBy(wsp.*)).between('['.p, ']'.p)

    private val marker_op    = PEP440.version_cmp | (wsp.*.with1 *> "in".p) | (wsp.*.with1 *> "not".p ~ wsp.+ ~ "in".p)
    private val python_str_c = wsp | alpha | digit | P.charIn("().{}-_*#:;,/?[]!~`@$%^&=+|<>")

    private val dquote = '"'.p
    private val squote = "'".p
    private val python_str =
      squote *> (python_str_c | dquote).*.string <* squote | //
        dquote *> (python_str_c | squote).*.string <* dquote //
    private val env_var =
      "python_version".p |
        "python_full_version".p |
        "os_name".p |
        "sys_platform".p |
        "platform_release".p |
        "platform_system".p |
        "platform_version".p |
        "platform_machine".p |
        "platform_python_implementation".p |
        "implementation_name".p |
        "implementation_version".p |
        "extra".p // ONLY when defined by a containing layer).string

    private val marker_or = P.recursive[String] { marker =>
      val marker_var  = wsp.*.with1 *> (env_var | python_str)
      val marker_expr = ((marker_var ~ marker_op ~ marker_var) | wsp.*.with1 ~ '('.p ~ marker ~ wsp.* ~ ')'.p).string
      val marker_and  = (marker_expr ~ wsp.* ~ "and".p ~ marker_expr | marker_expr).string
      val marker_or   = (marker_and ~ wsp.* ~ "or".p ~ marker_and | marker_and).string
      marker_or
    }

    private val quoted_marker = ';'.p ~ wsp.* *> marker_or

    private val name_req = //
      ((PEP440.identifier <* wsp.*) ~
        (extras.?.map(_.fold(Nil)(_.toList)) <* wsp.*) ~
        (PEP440.versionspec.?.map(_.fold(Nil)(_.toList)) <* wsp.*) ~
        (quoted_marker.? <* wsp.*))
        .map { case (((name, extras), verSpecs), marker) => PEP508Dependency(name, extras, verSpecs, marker) }

    private val pep508                                        = name_req.surroundedBy(wsp.*)
    def apply(raw: String): Either[P.Error, PEP508Dependency] = pep508.parseAll(raw)

  }

}
