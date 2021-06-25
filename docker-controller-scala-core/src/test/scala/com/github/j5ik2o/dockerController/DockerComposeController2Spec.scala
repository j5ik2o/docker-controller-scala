package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.{ DockerClientConfig, DockerClientImpl }
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import org.apache.commons.io.IOUtils
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll }
import org.seasar.util.io.ResourceUtil
import org.slf4j.{ Logger, LoggerFactory }

import java.io.{ File, InputStream }
import java.net.{ HttpURLConnection, URL }
import java.time.Instant
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._

class DockerComposeController2Spec extends AnyFreeSpec with BeforeAndAfter with BeforeAndAfterAll {
  val logger: Logger = LoggerFactory.getLogger(getClass)

  val dockerClientConfig: DockerClientConfig = DockerClientConfigUtil.buildConfigAwareOfDockerMachine()

  val dockerClient: DockerClient = {
    val httpClient: ApacheDockerHttpClient = new ApacheDockerHttpClient.Builder()
      .dockerHost(dockerClientConfig.getDockerHost).sslConfig(dockerClientConfig.getSSLConfig).build()
    DockerClientImpl.getInstance(dockerClientConfig, httpClient)
  }

  val host: String = DockerClientConfigUtil.dockerHost(dockerClientConfig)

  val hostPort: Int = RandomPortUtil.temporaryServerPort()

  val url = new URL(s"http://$host:$hostPort")

  def wget: Unit = {
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

  var dockerController: DockerController = _

  override protected def beforeAll(): Unit = {
    val buildDir: File                = ResourceUtil.getBuildDir(getClass)
    val dockerComposeWorkingDir: File = new File(buildDir, "docker-compose")
    dockerController = DockerComposeController(dockerClient)(
      dockerComposeWorkingDir,
      "docker-compose-3.yml.ftl",
      Seq("settings.env.ftl"),
      Map("hostPort" -> hostPort.toString, "message" -> Instant.now().toString)
    )
    dockerController.pullImageIfNotExists()
    dockerController.createContainer()
  }

  override protected def afterAll(): Unit = {
    dockerController.removeContainer()
    dockerClient.close()
  }

  before {
    dockerController.startContainer()
    dockerController.awaitCondition(Duration.Inf)(
      _.toString.contains("Listening on port 3000")
    )
    Thread.sleep(1000)
  }

  after {
    dockerController.stopContainer()
  }

  "DockerComposeController" - {
    "run-1" in {
      wget
    }
    "run-2" in {
      wget
    }

  }
}
