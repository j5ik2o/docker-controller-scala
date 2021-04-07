package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.dockerjava.core.{ DockerClientConfig, DockerClientImpl }
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import org.apache.commons.io.IOUtils
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll }
import org.slf4j.{ Logger, LoggerFactory }

import java.io.InputStream
import java.net.{ HttpURLConnection, URL }
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._

class DockerControllerSpec extends AnyFreeSpec with BeforeAndAfter with BeforeAndAfterAll {

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

  logger.debug(s"host = $host")
  logger.debug(s"hostPort = $hostPort")

  var dockerController: DockerController = _

  override protected def beforeAll(): Unit = {
    dockerController = new DockerControllerImpl(dockerClient)(
      imageName = "nginx",
      tag = Some("latest")
    ) {

      override protected def newCreateContainerCmd(): CreateContainerCmd = {
        val http        = ExposedPort.tcp(80)
        val portBinding = new Ports()
        portBinding.bind(http, Ports.Binding.bindPort(hostPort))

        super
          .newCreateContainerCmd()
          .withExposedPorts(http)
          .withHostConfig(newHostConfig().withPortBindings(portBinding))
      }
    }
    dockerController
      .pullImageIfNotExists()
      .createContainer()
  }

  override protected def afterAll(): Unit = {
    dockerController.removeContainer()
    dockerClient.close()
  }

  before {
    dockerController
      .startContainer()
      .awaitCondition(Duration.Inf)(_.toString.contains("Configuration complete; ready for start up"))
    Thread.sleep(1000)
  }

  after {
    dockerController.stopContainer()
  }

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

  "DockerController" - {
    "test-1" in {
      wget
    }
    "test-2" in {
      wget
    }
  }

}
