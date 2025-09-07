package com.github.j5ik2o.dockerController.postgresql

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.DockerControllerImpl
import com.github.j5ik2o.dockerController.postgresql.PostgreSQLController.{
  DefaultContainerPort,
  DefaultImageName,
  DefaultImageTag
}

import scala.concurrent.duration._

object PostgreSQLController {
  final val DefaultImageName: String        = "postgres"
  final val DefaultImageTag: Option[String] = Some("17.6")
  final val DefaultContainerPort: Int       = 5432

  def apply(
      dockerClient: DockerClient,
      isDockerClientAutoClose: Boolean = false,
      outputFrameInterval: FiniteDuration = 500.millis,
      imageName: String = DefaultImageName,
      imageTag: Option[String] = DefaultImageTag,
      envVars: Map[String, String] = Map.empty
  )(
      hostPort: Int,
      userName: String,
      password: Option[String] = None,
      databaseName: Option[String] = None,
      initDbArgs: Option[String] = None,
      initDbWalDir: Option[String] = None,
      hostAuthMethod: Option[String] = None,
      pgData: Option[String] = None
  ): PostgreSQLController =
    new PostgreSQLController(dockerClient, isDockerClientAutoClose, outputFrameInterval, imageName, imageTag, envVars)(
      hostPort,
      userName,
      password,
      databaseName,
      initDbArgs,
      initDbWalDir,
      hostAuthMethod,
      pgData
    )
}

class PostgreSQLController(
    dockerClient: DockerClient,
    isDockerClientAutoClose: Boolean = false,
    outputFrameInterval: FiniteDuration = 500.millis,
    imageName: String = DefaultImageName,
    imageTag: Option[String] = DefaultImageTag,
    envVars: Map[String, String] = Map.empty
)(
    hostPort: Int,
    userName: String,
    password: Option[String] = None,
    databaseName: Option[String] = None,
    initDbArgs: Option[String] = None,
    initDbWalDir: Option[String] = None,
    hostAuthMethod: Option[String] = None,
    pgData: Option[String] = None
) extends DockerControllerImpl(dockerClient, isDockerClientAutoClose, outputFrameInterval)(imageName, imageTag) {

  private val environmentVariables: Map[String, String] = {
    envVars ++
    Map[String, String](
      "POSTGRES_USER" -> userName
    ) ++
    password.map(s => Map("POSTGRES_PASSWORD" -> s)).getOrElse(Map.empty) ++
    databaseName.map(s => Map("POSTGRES_DB" -> s)).getOrElse(Map.empty) ++
    initDbArgs.map(s => Map("POSTGRES_INITDB_ARGS" -> s)).getOrElse(Map.empty) ++
    initDbWalDir.map(s => Map("POSTGRES_INITDB_WALDIR" -> s)).getOrElse(Map.empty) ++
    hostAuthMethod.map(s => Map("POSTGRES_HOST_AUTH_METHOD" -> s)).getOrElse(Map.empty) ++
    pgData.map(s => Map("PGDATA" -> s)).getOrElse(Map.empty)
  }

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val containerPort = ExposedPort.tcp(DefaultContainerPort)
    val portBinding   = new Ports()
    portBinding.bind(containerPort, Ports.Binding.bindPort(hostPort))
    super
      .newCreateContainerCmd()
      .withEnv(environmentVariables.map { case (k, v) => s"$k=$v" }.toArray: _*)
      .withExposedPorts(containerPort)
      .withHostConfig(newHostConfig().withPortBindings(portBinding))
  }
}
