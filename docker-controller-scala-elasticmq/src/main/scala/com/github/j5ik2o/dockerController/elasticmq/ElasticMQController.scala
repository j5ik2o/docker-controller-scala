package com.github.j5ik2o.dockerController.elasticmq

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.DockerControllerImpl
import com.github.j5ik2o.dockerController.elasticmq.ElasticMQController.{
  DefaultContainerPorts,
  DefaultImageName,
  DefaultImageTag
}

import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.jdk.CollectionConverters._

object ElasticMQController {
  final val DefaultImageName: String        = "softwaremill/elasticmq"
  final val DefaultImageTag: Option[String] = Some("1.6.14")
  final val DefaultContainerPorts: Seq[Int] = Seq(9324, 9325)

  def apply(
      dockerClient: DockerClient,
      isDockerClientAutoClose: Boolean = false,
      outputFrameInterval: FiniteDuration = 500.millis,
      imageName: String = DefaultImageName,
      imageTag: Option[String] = DefaultImageTag,
      envVars: Map[String, String] = Map.empty
  )(dockerHost: String, hostPorts: Seq[Int]): ElasticMQController =
    new ElasticMQController(dockerClient, isDockerClientAutoClose, outputFrameInterval, imageName, imageTag, envVars)(
      dockerHost,
      hostPorts
    )
}

class ElasticMQController(
    dockerClient: DockerClient,
    isDockerClientAutoClose: Boolean = false,
    outputFrameInterval: FiniteDuration = 500.millis,
    imageName: String = DefaultImageName,
    imageTag: Option[String] = DefaultImageTag,
    envVars: Map[String, String] = Map.empty
)(dockerHost: String, hostPorts: Seq[Int])
    extends DockerControllerImpl(dockerClient, isDockerClientAutoClose, outputFrameInterval)(imageName, imageTag) {

  private val environmentVariables = Map(
    "JAVA_OPTS"                             -> "-Dconfig.override_with_env_vars=true",
    "CONFIG_FORCE_node__address_host"       -> "*",
    "CONFIG_FORCE_rest__sqs_bind__hostname" -> "0.0.0.0",
    "CONFIG_FORCE_generate__node__address"  -> "false"
  ) ++
    envVars

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val containerPorts = DefaultContainerPorts.map(ExposedPort.tcp)
    val ports          = new Ports()
    containerPorts.zip(hostPorts).foreach { case (containerPort, hostPort) =>
      ports.bind(containerPort, Ports.Binding.bindPort(hostPort))
    }
    super
      .newCreateContainerCmd()
      .withEnv(environmentVariables.map { case (k, v) => s"$k=$v" }.toArray: _*)
      .withExposedPorts(containerPorts.toList.asJava)
      .withHostConfig(newHostConfig().withPortBindings(ports))
  }
}
