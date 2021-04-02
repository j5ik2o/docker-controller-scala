package com.github.j5ik2o.dockerController

import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory

import java.io.InputStream
import java.net.{ HttpURLConnection, URL }
import scala.jdk.CollectionConverters._

object HttpRequestUtil {

  private val logger = LoggerFactory.getLogger(getClass)

  def wget(url: URL): Unit = {
    var connection: HttpURLConnection = null
    var in: InputStream               = null
    try {
      connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")
      connection.connect()
      val responseCode = connection.getResponseCode
      assert(responseCode == HttpURLConnection.HTTP_OK)
      in = connection.getInputStream
      val lines = IOUtils.readLines(in, "UTF-8").asScala.mkString("\n")
      logger.debug(lines)
    } finally {
      if (in != null)
        in.close()
      if (connection != null)
        connection.disconnect()
    }
  }
}
