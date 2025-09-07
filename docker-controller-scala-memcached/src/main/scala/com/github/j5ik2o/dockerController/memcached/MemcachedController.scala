package com.github.j5ik2o.dockerController.memcached

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.j5ik2o.dockerController.DockerControllerImpl
import com.github.j5ik2o.dockerController.memcached.MemcachedController._

import scala.concurrent.duration._

object MemcachedController {
  final val DefaultImageName: String        = "memcached"
  final val DefaultImageTag: Option[String] = Some("trixie")
  final val DefaultContainerPort: Int       = 11211

  def apply(
      dockerClient: DockerClient,
      isDockerClientAutoClose: Boolean = false,
      outputFrameInterval: FiniteDuration = 500.millis,
      imageName: String = DefaultImageName,
      imageTag: Option[String] = DefaultImageTag,
      envVars: Map[String, String] = Map.empty
  )(
      hostPort: Int,
      prometheusEnabled: Boolean = false
  ): MemcachedController =
    new MemcachedController(dockerClient, isDockerClientAutoClose, outputFrameInterval, imageName, imageTag, envVars)(
      hostPort,
      prometheusEnabled
    )
}

class MemcachedController(
    dockerClient: DockerClient,
    isDockerClientAutoClose: Boolean = false,
    outputFrameInterval: FiniteDuration = 500.millis,
    imageName: String = DefaultImageName,
    imageTag: Option[String] = DefaultImageTag,
    envVars: Map[String, String] = Map.empty
)(
    hostPort: Int,
    prometheusEnabled: Boolean = false
) extends DockerControllerImpl(dockerClient, isDockerClientAutoClose, outputFrameInterval)(imageName, imageTag) {

  private val environmentVariables = Map(
    "MEMCACHED_PROMETHEUS_ENABLED" -> prometheusEnabled.toString
  ) ++
    envVars

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val containerPort = ExposedPort.tcp(DefaultContainerPort)
    val portBinding   = new Ports
    portBinding.bind(containerPort, Ports.Binding.bindPort(hostPort))
    super
      .newCreateContainerCmd()
      .withEnv(environmentVariables.map { case (k, v) => s"$k=$v" }.toArray: _*)
      .withExposedPorts(containerPort)
      .withHostConfig(newHostConfig().withPortBindings(portBinding))
  }
}
