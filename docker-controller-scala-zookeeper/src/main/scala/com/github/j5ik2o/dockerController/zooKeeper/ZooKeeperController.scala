package com.github.j5ik2o.dockerController.zooKeeper

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.zooKeeper.ZooKeeperController._
import com.github.j5ik2o.dockerController.{ DockerControllerImpl, NetworkAlias }

import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.util.matching.Regex

object ZooKeeperController {
  final val DefaultImageName              = "zookeeper"
  final val DefaultImageTag: Some[String] = Some("3.5")
  final val DefaultZooPort                = 2181
  final val RegexForWaitPredicate: Regex  = """binding to port /0.0.0.0:.*""".r

  def apply(
      dockerClient: DockerClient,
      isDockerClientAutoClose: Boolean = false,
      outputFrameInterval: FiniteDuration = 500.millis,
      imageName: String = DefaultImageName,
      imageTag: Option[String] = DefaultImageTag,
      envVars: Map[String, String] = Map.empty
  )(
      myId: Int,
      hostPort: Int,
      containerPort: Int = DefaultZooPort,
      networkAlias: Option[NetworkAlias] = None
  ): ZooKeeperController =
    new ZooKeeperController(dockerClient, isDockerClientAutoClose, outputFrameInterval, imageName, imageTag, envVars)(
      myId,
      hostPort,
      containerPort,
      networkAlias
    )
}

class ZooKeeperController(
    dockerClient: DockerClient,
    isDockerClientAutoClose: Boolean = false,
    outputFrameInterval: FiniteDuration = 500.millis,
    imageName: String = DefaultImageName,
    imageTag: Option[String] = DefaultImageTag,
    envVars: Map[String, String] = Map.empty
)(
    myId: Int,
    hostPort: Int,
    val containerPort: Int,
    networkAlias: Option[NetworkAlias] = None
) extends DockerControllerImpl(dockerClient, isDockerClientAutoClose, outputFrameInterval)(imageName, imageTag) {

  private def environmentVariables(myId: Int): Map[String, String] = {
    Map(
      "ZOO_MY_ID" -> myId.toString,
      "ZOO_PORT"  -> containerPort.toString
    ) ++ envVars
  }

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val zooPort     = ExposedPort.tcp(containerPort)
    val portBinding = new Ports()
    portBinding.bind(zooPort, Ports.Binding.bindPort(hostPort))
    val defaultHostConfig = newHostConfig.withPortBindings(portBinding)
    val hostConfig = networkAlias.fold(defaultHostConfig) { n => defaultHostConfig.withNetworkMode(n.network.id) }
    val result = super
      .newCreateContainerCmd()
      .withEnv(environmentVariables(myId).map { case (k, v) => s"$k=$v" }.toArray: _*)
      .withExposedPorts(zooPort, ExposedPort.tcp(2888), ExposedPort.tcp(3888))
      .withHostConfig(hostConfig)
    networkAlias.fold(result) { n => result.withAliases(n.name) }
  }
}
