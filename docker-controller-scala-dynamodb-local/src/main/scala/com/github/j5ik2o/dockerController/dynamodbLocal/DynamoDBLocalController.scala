package com.github.j5ik2o.dockerController.dynamodbLocal

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.DockerControllerImpl
import com.github.j5ik2o.dockerController.dynamodbLocal.DynamoDBLocalController._

import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.util.matching.Regex

object DynamoDBLocalController {
  final val DefaultImageName: String        = "amazon/dynamodb-local"
  final val DefaultImageTag: Option[String] = Some("1.18.0")
  final val DefaultContainerPort: Int       = 8000
  final val RegexOfWaitPredicate: Regex     = s"""Port.*$DefaultContainerPort.*""".r

  def apply(
      dockerClient: DockerClient,
      outputFrameInterval: FiniteDuration = 500.millis,
      imageName: String = DefaultImageName,
      imageTag: Option[String] = DefaultImageTag,
      envVars: Map[String, String] = Map.empty
  )(
      hostPort: Int
  ): DynamoDBLocalController =
    new DynamoDBLocalController(dockerClient, outputFrameInterval, imageName, imageTag, envVars)(hostPort)
}

class DynamoDBLocalController(
    dockerClient: DockerClient,
    outputFrameInterval: FiniteDuration = 500.millis,
    imageName: String = DefaultImageName,
    imageTag: Option[String] = DefaultImageTag,
    envVars: Map[String, String] = Map.empty
)(
    hostPort: Int
) extends DockerControllerImpl(dockerClient, outputFrameInterval)(imageName, imageTag) {

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val containerPort = ExposedPort.tcp(DefaultContainerPort)
    val portBinding   = new Ports()
    portBinding.bind(containerPort, Ports.Binding.bindPort(hostPort))
    super
      .newCreateContainerCmd()
      .withCmd("-jar", "DynamoDBLocal.jar", "-dbPath", ".", "-sharedDb")
      .withEnv(envVars.map { case (k, v) => s"$k=$v" }.toArray: _*)
      .withExposedPorts(containerPort)
      .withHostConfig(newHostConfig().withPortBindings(portBinding))
  }

}
