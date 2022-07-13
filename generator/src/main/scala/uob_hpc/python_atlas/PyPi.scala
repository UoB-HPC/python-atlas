package uob_hpc.python_atlas

import uob_hpc.python_atlas.Pickler.{*, given}

import scala.collection.immutable.ArraySeq

object PyPi {

  // See https://peps.python.org/pep-0691/
  object Pep681 {
    case class RepoIndex(meta: RepoIndex.Meta, projects: ArraySeq[RepoIndex.Entry])
    object RepoIndex {
      case class Entry(name: String)
      case class Meta(`_last-serial`: Long, `api-version`: String)

      given Reader[Entry]     = macroR
      given Reader[Meta]      = macroR
      given Reader[RepoIndex] = macroR

      def apply(): Either[Throwable, Option[RepoIndex]] =
        Rest.getAndParseJson[RepoIndex]("https://pypi.org/simple/?format=application/vnd.pypi.simple.v1+json")
    }
  }

  // See https://warehouse.pypa.io/api-reference/json.html
  // There isn't a PEP for this, see https://github.com/pypa/packaging-problems/issues/367
  // There's a PEP for the HTML API (PEP503) but we're not using that.
  object Warehouse {

    case class Package(
        info: Package.Info,
        last_serial: Long,
        releases: Map[String, ArraySeq[Package.Release]] = Map.empty,
        urls: ArraySeq[Package.Release] = ArraySeq.empty
    )
    object Package {

      case class Release(
          comment_text: String,
          digests: Map[String, String],
          downloads: Long,
          filename: String,
          has_sig: Boolean,
          md5_digest: String,
          packagetype: String,
          python_version: String,
          requires_python: String,
          size: Long,
          upload_time: String,
          upload_time_iso_8601: String,
          url: String,
          yanked: Boolean,
          yanked_reason: Option[String] = None
      )

      case class Info(
          author: String,
          author_email: String,
          bugtrack_url: Option[String] = None,
          classifiers: ArraySeq[String],
          description: Option[String] = None,
          description_content_type: Option[String] = None,
          docs_url: Option[String] = None,
          download_url: Option[String] = None,
          downloads: Map[String, Int],
          home_page: Option[String] = None,
          keywords: Option[String] = None,
          license: Option[String] = None,
          maintainer: Option[String] = None,
          maintainer_email: Option[String] = None,
          name: String,
          package_url: Option[String] = None,
          platform: Option[String] = None,
          project_url: Option[String] = None,
          project_urls: Map[String, String],
          release_url: Option[String] = None,
          requires_dist: Option[ArraySeq[String]] = None,
          requires_python: Option[String] = None,
          summary: String,
          version: String,
          yanked: Boolean,
          yanked_reason: Option[String] = None
      )

      given Reader[Release] = macroR
      given Reader[Info]    = macroR
      given Reader[Package] = macroR

      def apply(name: String) = Rest.getAndParseJson[Package](s"https://pypi.org/pypi/$name/json")
    }

  }

}
