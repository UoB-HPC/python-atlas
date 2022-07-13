package uob_hpc.python_atlas

import cats.syntax.all.*
import sttp.client3.*
import sttp.model.{StatusCode, Uri}
import uob_hpc.python_atlas.Pickler

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.security.MessageDigest

object Rest {

  private final val Backend = HttpURLConnectionBackend(customizeConnection = _.setUseCaches(true))

  private def md5(s: String) =
    String.format(
      "%032x",
      new BigInteger(1, MessageDigest.getInstance("md5").digest(s.getBytes(StandardCharsets.UTF_8)))
    )

  def getAndParseJson[A: Pickler.Reader](url: String): Either[Throwable, Option[A]] = {

    val uri = uri"$url"

    val f         = s"${md5(uri.toString)}_${uri.path.mkString("_")}"
    val cacheFile = Paths.get("data").resolve(f).toAbsolutePath
    val content =
      if (Files.isRegularFile(cacheFile) && Files.size(cacheFile) > 0) {
        Either.catchNonFatal {
          val data = Files.readString(cacheFile, StandardCharsets.UTF_8)
//          println(s"Cache hit for $uri: $f = ${data.length} chars")
          Some(data)
        }
      } else {
        val request = basicRequest
          .get(uri)
          .contentType("application/json")
          .acceptEncoding("gzip, deflate")
        val response = request.send(Backend)
        if (response.code == StatusCode.NotFound) {
          println(s"GET $uri 404")
          Right(None)
        } else {
          println("GET" + response.headers.mkString("\n"))
          val result = response.body.left.map(new RuntimeException(_)).map(Some(_))
          result.foreach(s =>
            Files.writeString(
              cacheFile,
              s.get,
              StandardCharsets.UTF_8,
              StandardOpenOption.TRUNCATE_EXISTING,
              StandardOpenOption.WRITE,
              StandardOpenOption.CREATE_NEW
            )
          )
          result
        }

      }
    content.map(_.map(Pickler.read[A](_, trace = true)))
  }

}
