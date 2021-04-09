package com.github.j5ik2o.dockerController.dynamodbLocal

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Frame, Ports }
import com.github.j5ik2o.dockerController.{ DockerController, DockerControllerImpl }

import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration }
import scala.util.matching.Regex

object DynamoDBLocalController {
  private val regex: Regex = """Port.*8000.*""".r

  final val awaitCondition: Frame => Boolean = { frame: Frame =>
    regex.findFirstMatchIn(new String(frame.getPayload)).isDefined
  }
}

class DynamoDBLocalController(dockerClient: DockerClient, outputFrameInterval: FiniteDuration = 500.millis)(
    hostPort: Int
) extends DockerControllerImpl(dockerClient, outputFrameInterval)("amazon/dynamodb-local", Some("1.13.2")) {

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val containerPort = ExposedPort.tcp(8000)
    val portBinding   = new Ports()
    portBinding.bind(containerPort, Ports.Binding.bindPort(hostPort))
    super
      .newCreateContainerCmd()
      .withCmd("-jar", "DynamoDBLocal.jar", "-dbPath", ".", "-sharedDb")
      .withExposedPorts(containerPort)
      .withHostConfig(newHostConfig().withPortBindings(portBinding))
  }

}
