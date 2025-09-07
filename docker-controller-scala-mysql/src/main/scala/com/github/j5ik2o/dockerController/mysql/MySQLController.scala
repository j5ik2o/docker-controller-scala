package com.github.j5ik2o.dockerController.mysql

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.{ CreateContainerCmd, RemoveContainerCmd }
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.DockerControllerImpl
import com.github.j5ik2o.dockerController.mysql.MySQLController._

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

object MySQLController {
  final val DefaultImageName: String        = "mysql"
  final val DefaultImageTag: Option[String] = Some("9.4.0")
  final val DefaultContainerPort: Int       = 3306

  def apply(
      dockerClient: DockerClient,
      isDockerClientAutoClose: Boolean = false,
      outputFrameInterval: FiniteDuration = 500.millis,
      imageName: String = DefaultImageName,
      imageTag: Option[String] = DefaultImageTag,
      envVars: Map[String, String] = Map.empty
  )(
      hostPort: Int,
      rootPassword: String,
      userNameAndPassword: Option[MySQLUserNameAndPassword] = None,
      databaseName: Option[String] = None
  ): MySQLController =
    new MySQLController(dockerClient, isDockerClientAutoClose, outputFrameInterval, imageName, imageTag, envVars)(
      hostPort,
      rootPassword,
      userNameAndPassword,
      databaseName
    )
}

class MySQLController(
    dockerClient: DockerClient,
    isDockerClientAutoClose: Boolean = false,
    outputFrameInterval: FiniteDuration = 500.millis,
    imageName: String = DefaultImageName,
    imageTag: Option[String] = DefaultImageTag,
    envVars: Map[String, String] = Map.empty
)(
    hostPort: Int,
    rootPassword: String,
    userNameAndPassword: Option[MySQLUserNameAndPassword] = None,
    databaseName: Option[String] = None
) extends DockerControllerImpl(dockerClient, isDockerClientAutoClose, outputFrameInterval)(imageName, imageTag) {

  private val environmentVariables: Map[String, String] = {
    val env1 = Map[String, String](
      "MYSQL_ROOT_PASSWORD" -> rootPassword
    ) ++ envVars
    val env2 = userNameAndPassword.fold(env1) { case MySQLUserNameAndPassword(u, p) =>
      env1 ++ Map("MYSQL_USER" -> u, "MYSQL_PASSWORD" -> p)
    }
    databaseName.fold(env2) { name => env2 ++ Map("MYSQL_DATABASE" -> name) }
  }

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val containerPort = ExposedPort.tcp(DefaultContainerPort)
    val portBinding   = new Ports()
    portBinding.bind(containerPort, Ports.Binding.bindPort(hostPort))
    val cmd = super
      .newCreateContainerCmd()
      .withEnv(environmentVariables.map { case (k, v) => s"$k=$v" }.toArray: _*)
      .withExposedPorts(containerPort)
      .withHostConfig(newHostConfig().withPortBindings(portBinding))
    cmd
  }

  override protected def newRemoveContainerCmd(): RemoveContainerCmd = {
    require(containerId.isDefined)
    dockerClient.removeContainerCmd(containerId.get).withForce(true)
  }

}
