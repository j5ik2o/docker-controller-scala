package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.{ CreateContainerCmd, StartContainerCmd }
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ AccessMode, Bind, Binds, ExposedPort, Ports, SELContext, Volume }
import com.github.dockerjava.core.{ DockerClientConfig, DockerClientImpl }
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import org.apache.commons.io.IOUtils
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll }
import org.scalatest.freespec.AnyFreeSpec
import org.seasar.util.io.{ FileUtil, ResourceUtil }
import org.slf4j.{ Logger, LoggerFactory }

import java.io.{ File, InputStream }
import java.net.{ HttpURLConnection, URL }
import java.nio.file.{ Files, Path, Paths }
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._

class DockerComposeControllerSpec extends AnyFreeSpec with BeforeAndAfter with BeforeAndAfterAll {
  val logger: Logger = LoggerFactory.getLogger(getClass)

  val dockerClientConfig: DockerClientConfig = DockerClientConfigUtil.buildConfigAwareOfDockerMachine()

  val dockerClient: DockerClient = {
    val httpClient: ApacheDockerHttpClient = new ApacheDockerHttpClient.Builder()
      .dockerHost(dockerClientConfig.getDockerHost).sslConfig(dockerClientConfig.getSSLConfig).build()
    DockerClientImpl.getInstance(dockerClientConfig, httpClient)
  }

  val host: String =
    if (dockerClientConfig.getDockerHost.getHost == null)
      "127.0.0.1"
    else
      dockerClientConfig.getDockerHost.getHost

  val hostPort: Int = RandomPortUtil.temporaryServerPort()

  val url = new URL(s"http://$host:$hostPort")

  def wget = {
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
      println(lines)
    } catch {
      case ex: Throwable =>
        ex.printStackTrace()
    } finally {
      if (in != null)
        in.close()
      if (connection != null)
        connection.disconnect()
    }
  }
  val buildDir: File                = ResourceUtil.getBuildDir(getClass)
  val dockerComposeWorkingDir: File = new File(buildDir, "docker-compose")

  val controller: DockerComposeController =
    new DockerComposeController(dockerClient)(
      dockerComposeWorkingDir,
      "docker-compose-2.yml.ftl",
      Map("nginxHostPort" -> hostPort.toString)
    )

  "DockerComposeController" - {
    "run" in {
      controller.pullImageIfNotExists()
      controller.createContainer()
      controller
        .startContainer().awaitCondition(Duration.Inf)(
          _.toString.contains("Configuration complete; ready for start up")
        )
      Thread.sleep(1000)
      wget
      controller.stopContainer()
      controller.removeContainer()
    }

  }
}
