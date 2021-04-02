package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.dockerjava.core.{ DefaultDockerClientConfig, DockerClientBuilder }
import org.scalatest.BeforeAndAfter
import org.scalatest.freespec.AnyFreeSpec
import scalaj.http.Http

import scala.concurrent.duration.Duration

class DockerControllerSpec extends AnyFreeSpec with BeforeAndAfter {

  val dockerClientConfig: DefaultDockerClientConfig = DockerClientConfigBuilder.loadDockerClientConfig()
  val dockerClient: DockerClient                    = DockerClientBuilder.getInstance(dockerClientConfig).build()

  val dockerHost: String = dockerClientConfig.getDockerHost.getHost
  val hostPort: Int      = RandomPortUtil.temporaryServerPort()

  var dockerController: DockerController = _

  before {
    dockerController = new DockerController(dockerClient)(
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
      .startContainer()
      .awaitCondition(Duration.Inf)(_.toString.contains("Configuration complete; ready for start up"))
  }

  after {
    dockerController.stopContainer()
  }

  "DockerController" - {
    "test-1" in {
      val response = Http(s"http://$dockerHost:$hostPort").asString
      println(s"response = $response")
    }
    "test-2" in {
      val response = Http(s"http://$dockerHost:$hostPort").asString
      println(s"response = $response")
    }
  }
}
