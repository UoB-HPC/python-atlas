package uob_hpc.python_atlas

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

object IO {

  def write(path: Path)(data: String): Path = {
    Files.writeString(
      path,
      data,
      StandardCharsets.UTF_8,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE
    )
    println(s"Wrote ${Files.size(path) / 1024} KiB to $path")
    path
  }

}
