package com.github.j5ik2o.dockerController

import java.io.{ File, FileNotFoundException, IOException }

private[dockerController] object ComposeResourceFile {
  def resolve(name: String): File = {
    require(name != null && name.nonEmpty, "resource name must not be empty")
    val loader = Thread.currentThread().getContextClassLoader
    require(loader != null, "context class loader must not be null")
    val url = Option(loader.getResource(name)).getOrElse(throw new FileNotFoundException(name))
    if (url.getProtocol != "file")
      throw new IOException(s"Compose requires a file resource: $name ($url)")
    val file = new File(url.toURI)
    if (!file.isFile) throw new FileNotFoundException(file.toString)
    file
  }
}
