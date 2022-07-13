package uob_hpc.python_atlas

import cats.data.NonEmptyList
import cats.syntax.all.*
import uob_hpc.python_atlas.Pickler.{*, given}
import uob_hpc.python_atlas.Py.RequirementParsers
import uob_hpc.python_atlas.Py.RequirementParsers.*
import uob_hpc.python_atlas.Rest

import java.time.Instant
import scala.collection.immutable.ArraySeq

object Conda {

  given Reader[CondaPackagePinning] =
    readwriter[String]
      .map[CondaPackagePinning](s =>
        CondaPackagePinning(s).fold(e => throw new RuntimeException(s"Cannot parse `$s`: ${e.toString}"), identity)
      )

  case class ChannelData(
      channeldata_version: Int,
      packages: Map[String, ChannelData.Package],
      subdirs: ArraySeq[String]
  )
  object ChannelData {
    case class Package(
        `activate.d`: Boolean,
        binary_prefix: Boolean,
        `deactivate.d`: Boolean,
        description: String = "",
        dev_url: StringList = StringList.empty,
        doc_source_url: StringList = StringList.empty,
        doc_url: StringList = StringList.empty,
        home: String = "",
        icon_hash: String = "",
        icon_url: StringList = StringList.empty,
        identifiers: ArraySeq[Map[String, String]] = ArraySeq.empty,
        keywords: ArraySeq[String] = ArraySeq.empty,
        license: String = "",
        post_link: Boolean,
        pre_link: Boolean,
        pre_unlink: Boolean,
        recipe_origin: String = "",
        run_exports: Map[String, Map[String, ArraySeq[String]]],
        source_git_url: StringList = StringList.empty,
        source_url: StringList = StringList.empty,
        subdirs: ArraySeq[String],
        summary: String = "",
        tags: String = "",
        text_prefix: Boolean,
        timestamp: Instant = Instant.MIN,
        version: String = ""
    )
    given Reader[Package]     = macroR
    given Reader[ChannelData] = macroR
    def apply(repo: String): Either[Throwable, Option[ChannelData]] =
      Rest.getAndParseJson[ChannelData](s"https://conda.anaconda.org/$repo/channeldata.json")
  }

  case class RepoData(
      repodata_version: Int,
      info: Map[String, String],
      packages: Map[String, RepoData.Package],
      `packages.conda`: Map[String, RepoData.Package],
      removed: ArraySeq[String]
  )
  object RepoData {

    case class Package(
        build: String,
        build_number: Long,
        depends: ArraySeq[CondaPackagePinning],
        md5: String,
        sha256: String,
        name: String,
        size: Long,
        subdir: String = "",
        timestamp: Instant = Instant.MIN, //
        version: String
    )

    given Reader[Package]  = macroR
    given Reader[RepoData] = macroR

    def apply(repo: String, subdir: String, current: Boolean = true): Either[Throwable, Option[RepoData]] =
      Rest.getAndParseJson[RepoData](
        s"https://conda.anaconda.org/$repo/$subdir/${if (current) "current_" else ""}repodata.json"
      )

  }

  // https://docs.conda.io/projects/conda-build/en/latest/resources/package-spec.html#package-match-specifications
  case class CondaPackagePinning(
      name: String,
      versionSpec: ArraySeq[NonEmptyList[(RequirementParsers.VerSpec, String)]],
      exactbuild: Option[String]
  )
  object CondaPackagePinning {

    import RequirementParsers.*
    import cats.parse.Parser as P
    import cats.parse.Rfc5234.*

    private val verSpec =
      (PEP440.version_cmp ~ PEP440.version).map(NonEmptyList.one(_)) |
        ('='.p.?.with1 *> PEP440.version).map(v => NonEmptyList.one((VerSpec.StringIs, v)))

    private val tripleSep = (P.charIn('=', ' ') ~ wsp.*).void

    private val nameIdent       = ('_'.p.* ~ PEP440.identifier).string
    private val verSpecs        = (verSpec.repSep('|'.p).map(_.flatten) | verSpec).repSep0(','.p)
    private val exactBuildIdent = (PEP440.letterOrDigit | P.charIn("-_.*+!")).+.string

    private val conda_build_pinning = ( //
      nameIdent ~
        (tripleSep *> verSpecs).?.map(_.fold(Nil)(_.toList).to(ArraySeq)) ~
        (tripleSep *> exactBuildIdent).?
    ).map { case ((name, specs), exactBuild) => CondaPackagePinning(name, specs, exactBuild) }

    def apply(raw: String): Either[P.Error, CondaPackagePinning] = conda_build_pinning.parseAll(raw)

  }

  // TODO add parquet
  //https://anaconda-package-data.s3.us-east-2.amazonaws.com/conda/hourly/2019/12/2019-12-.parquet
  //https://anaconda-package-data.s3.amazonaws.com/conda/hourly/2018/12/2018-12-31.parquet

}
