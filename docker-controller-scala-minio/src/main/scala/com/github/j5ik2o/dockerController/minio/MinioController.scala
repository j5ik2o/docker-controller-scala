package com.github.j5ik2o.dockerController.minio

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.DockerControllerImpl
import com.github.j5ik2o.dockerController.minio.MinioController._

import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.util.matching.Regex

object MinioController {
  final val DefaultImageName              = "minio/minio"
  final val DefaultImageTag: Some[String] = Some("RELEASE.2022-08-05T23-27-09Z")
  final val DefaultContainerPort          = 9000
  final val RegexForWaitPredicate: Regex  = """^Documentation: https://docs\.min\.io$""".r

  final val DefaultMinioAccessKeyId: String     = "AKIAIOSFODNN7EXAMPLE"
  final val DefaultMinioSecretAccessKey: String = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"

  def apply(
      dockerClient: DockerClient,
      outputFrameInterval: FiniteDuration = 500.millis,
      imageName: String = DefaultImageName,
      imageTag: Option[String] = DefaultImageTag,
      envVars: Map[String, String] = Map.empty
  )(
      hostPort: Int,
      minioAccessKeyId: String = DefaultMinioAccessKeyId,
      minioSecretAccessKey: String = DefaultMinioSecretAccessKey
  ): MinioController =
    new MinioController(dockerClient, outputFrameInterval, imageName, imageTag, envVars)(
      hostPort,
      minioAccessKeyId,
      minioSecretAccessKey
    )
}

class MinioController(
    dockerClient: DockerClient,
    outputFrameInterval: FiniteDuration = 500.millis,
    imageName: String = DefaultImageName,
    imageTag: Option[String] = DefaultImageTag,
    envVars: Map[String, String] = Map.empty
)(
    hostPort: Int,
    minioAccessKeyId: String = DefaultMinioAccessKeyId,
    minioSecretAccessKey: String = DefaultMinioSecretAccessKey
) extends DockerControllerImpl(dockerClient, outputFrameInterval)(imageName, imageTag) {

  private val environmentVariables = Map(
    "MINIO_ROOT_USER"     -> minioAccessKeyId,
    "MINIO_ROOT_PASSWORD" -> minioSecretAccessKey
  ) ++ envVars

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val containerPort = ExposedPort.tcp(DefaultContainerPort)
    val portBinding   = new Ports()
    portBinding.bind(containerPort, Ports.Binding.bindPort(hostPort))
    super
      .newCreateContainerCmd()
      .withCmd("server", "--compat", "/data")
      .withEnv(
        environmentVariables.map { case (k, v) => s"$k=$v" }.toArray: _*
      )
      .withExposedPorts(containerPort)
      .withHostConfig(newHostConfig().withPortBindings(portBinding))
  }

}
