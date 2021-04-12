package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.ZooKeeperController._

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

object ZooKeeperController {
  final val ImageName             = "zookeeper"
  final val ImageTag              = Some("3.4.9")
  final val DefaultZooPort        = 2181
  final val RegexForWaitPredicate = """binding to port 0.0.0.0/0.0.0.0:.*""".r

  def apply(dockerClient: DockerClient, outputFrameInterval: FiniteDuration = 500.millis)(
      myId: Int,
      host: String,
      hostPort: Int,
      containerPort: Int = DefaultZooPort,
      networkAlias: Option[NetworkAlias] = None
  ) = new ZooKeeperController(dockerClient, outputFrameInterval)(myId, host, hostPort, containerPort, networkAlias)
}

class ZooKeeperController(dockerClient: DockerClient, outputFrameInterval: FiniteDuration = 500.millis)(
    myId: Int,
    host: String,
    hostPort: Int,
    val containerPort: Int,
    networkAlias: Option[NetworkAlias] = None
) extends DockerControllerImpl(dockerClient, outputFrameInterval)(ImageName, ImageTag) {

  private def defaultEnvSettings(myId: Int) = Map(
    "ZOO_MY_ID"   -> myId.toString,
    "ZOO_PORT"    -> containerPort.toString,
    "ZOO_SERVERS" -> s"server.$myId=0.0.0.0:2888:3888"
  )

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val zooPort     = ExposedPort.tcp(containerPort)
    val portBinding = new Ports()
    portBinding.bind(zooPort, Ports.Binding.bindPort(hostPort))
    val defaultHostConfig = newHostConfig.withPortBindings(portBinding)
    val hostConfig        = networkAlias.fold(defaultHostConfig) { n => defaultHostConfig.withNetworkMode(n.network.id) }
    val result = super
      .newCreateContainerCmd()
      .withEnv(defaultEnvSettings(myId).map { case (k, v) => s"$k=$v" }.toArray: _*)
      .withExposedPorts(zooPort, ExposedPort.tcp(2888), ExposedPort.tcp(3888))
      .withHostConfig(hostConfig)
    networkAlias.fold(result) { n => result.withAliases(n.name) }
  }
}
