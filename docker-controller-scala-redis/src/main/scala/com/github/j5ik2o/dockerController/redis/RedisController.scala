package com.github.j5ik2o.dockerController.redis

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.DockerControllerImpl
import com.github.j5ik2o.dockerController.redis.RedisController._

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

object RedisController {
  final val DefaultImageName: String        = "redis"
  final val DefaultImageTag: Option[String] = Some("bookworm")
  final val DefaultContainerPort: Int       = 6379

  def apply(
      dockerClient: DockerClient,
      isDockerClientAutoClose: Boolean = false,
      outputFrameInterval: FiniteDuration = 500.millis,
      imageName: String = DefaultImageName,
      imageTag: Option[String] = DefaultImageTag,
      envVars: Map[String, String] = Map.empty
  )(
      hostPort: Int,
      allowEmptyPassword: Boolean = true,
      redisPassword: Option[String] = None,
      redisAofEnabled: Boolean = false
  ): RedisController =
    new RedisController(dockerClient, isDockerClientAutoClose, outputFrameInterval, imageName, imageTag, envVars)(
      hostPort,
      allowEmptyPassword,
      redisPassword,
      redisAofEnabled
    )
}

class RedisController(
    dockerClient: DockerClient,
    isDockerClientAutoClose: Boolean = false,
    outputFrameInterval: FiniteDuration = 500.millis,
    imageName: String = DefaultImageName,
    imageTag: Option[String] = DefaultImageTag,
    envVars: Map[String, String] = Map.empty
)(
    hostPort: Int,
    allowEmptyPassword: Boolean = true,
    redisPassword: Option[String] = None,
    redisAofEnabled: Boolean = false
) extends DockerControllerImpl(dockerClient, isDockerClientAutoClose, outputFrameInterval)(imageName, imageTag) {

  private val environmentVariables = Map(
    "ALLOW_EMPTY_PASSWORD" -> { if (allowEmptyPassword) "yes" else "no" },
    "REDIS_AOF_ENABLED"    -> { if (redisAofEnabled) "yes" else "no" }
  ) ++
    redisPassword.map(password => Map("REDIS_PASSWORD" -> password)).getOrElse(Map.empty) ++
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
