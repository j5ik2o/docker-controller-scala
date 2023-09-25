package com.github.j5ik2o.dockerController.elasticsearch

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.DockerControllerImpl
import com.github.j5ik2o.dockerController.elasticsearch.ElasticsearchController._

import scala.concurrent.duration._

object ElasticsearchController {
  final val DefaultImageName: String        = "docker.elastic.co/elasticsearch/elasticsearch"
  final val DefaultImageTag: Option[String] = Some("8.10.2")
  final val DefaultContainerPorts: Seq[Int] = Seq(9200, 9300)

  def apply(
      dockerClient: DockerClient,
      isDockerClientAutoClose: Boolean = false,
      outputFrameInterval: FiniteDuration = 500.millis,
      imageName: String = DefaultImageName,
      imageTag: Option[String] = DefaultImageTag,
      envVars: Map[String, String] = Map.empty
  )(
      hostPort1: Int,
      hostPort2: Int
  ): ElasticsearchController =
    new ElasticsearchController(
      dockerClient,
      isDockerClientAutoClose,
      outputFrameInterval,
      imageName,
      imageTag,
      envVars
    )(hostPort1, hostPort2)
}

class ElasticsearchController(
    dockerClient: DockerClient,
    isDockerClientAutoClose: Boolean = false,
    outputFrameInterval: FiniteDuration = 500.millis,
    imageName: String = DefaultImageName,
    imageTag: Option[String] = DefaultImageTag,
    envVars: Map[String, String] = Map.empty
)(
    hostPort1: Int,
    hostPort2: Int
) extends DockerControllerImpl(dockerClient, isDockerClientAutoClose, outputFrameInterval)(imageName, imageTag) {

  private val environmentVariables = Map(
    "discovery.type" -> "single-node",
    "xpack.security.enabled" -> "false"
//    "network.host" -> "0.0.0.0"
  ) ++ envVars

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val containerPorts = DefaultContainerPorts.map(ExposedPort.tcp)
    val portBinding    = new Ports()
    containerPorts.zip(Seq(hostPort1, hostPort2)).foreach { case (containerPort, hostPort) =>
      portBinding.bind(containerPort, Ports.Binding.bindPort(hostPort))
    }
    val result = super
      .newCreateContainerCmd()
      .withEnv(environmentVariables.map { case (k, v) => s"$k=$v" }.toArray: _*)
      .withExposedPorts(containerPorts: _*)
      .withHostConfig(newHostConfig().withPortBindings(portBinding))
    result
  }
}
