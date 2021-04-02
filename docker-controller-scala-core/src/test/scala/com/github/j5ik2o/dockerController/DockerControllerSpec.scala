package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.dockerjava.core.{ DefaultDockerClientConfig, DockerClientImpl }
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import org.scalatest.BeforeAndAfter
import org.scalatest.freespec.AnyFreeSpec
import scalaj.http.Http

import scala.concurrent.duration.Duration

class DockerControllerSpec extends AnyFreeSpec with BeforeAndAfter {
  val dockerHost = "192.168.99.107"

  val config = DefaultDockerClientConfig.createDefaultConfigBuilder
    .withDockerHost(s"tcp://$dockerHost:2376")
    .withDockerCertPath(System.getProperty("user.home") + "/.docker/machine/machines/default")
    .withDockerTlsVerify(true)
    .build()

  val httpClient = new ApacheDockerHttpClient.Builder()
    .dockerHost(config.getDockerHost)
    .sslConfig(config.getSSLConfig)
    .maxConnections(100).build

  val dockerClient = DockerClientImpl.getInstance(config, httpClient)

  val hostPort = RandomPortUtil.temporaryServerPort()

  val dockerController = new DockerController(dockerClient)(
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

  before {
    dockerController
      .pullImageIfNotExists()
      .createContainer()
      .startContainer()
      .awaitCondition(Duration.Inf)(_.toString.contains("Configuration complete; ready for start up"))
  }

  after {
    dockerController.stopContainer()
  }

  "DockerController" - {
    "test" in {
      val response = Http(s"http://$dockerHost:$hostPort").asString
      println(s"response = $response")
    }
  }
}
