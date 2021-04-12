package com.github.j5ik2o.dockerController.minio

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.DockerControllerImpl
import com.github.j5ik2o.dockerController.minio.MinioController._

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

object MinioController {
  final val ImageName             = "minio/minio"
  final val ImageTag              = Some("RELEASE.2021-03-17T02-33-02Z")
  final val DefaultContainerPort  = 9000
  final val RegexForWaitPredicate = """^Browser Access:.*""".r

  final val DefaultMinioAccessKeyId: String     = "AKIAIOSFODNN7EXAMPLE"
  final val DefaultMinioSecretAccessKey: String = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
}

class MinioController(dockerClient: DockerClient, outputFrameInterval: FiniteDuration = 500.millis)(
    hostPort: Int,
    minioAccessKeyId: String = DefaultMinioAccessKeyId,
    minioSecretAccessKey: String = DefaultMinioSecretAccessKey
) extends DockerControllerImpl(dockerClient, outputFrameInterval)(ImageName, ImageTag) {

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val containerPort = ExposedPort.tcp(DefaultContainerPort)
    val portBinding   = new Ports()
    portBinding.bind(containerPort, Ports.Binding.bindPort(hostPort))
    super
      .newCreateContainerCmd()
      .withCmd("server", "--compat", "/data")
      .withEnv(
        Map(
          "MINIO_ROOT_USER"     -> minioAccessKeyId,
          "MINIO_ROOT_PASSWORD" -> minioSecretAccessKey
        ).map { case (k, v) => s"$k=$v" }.toArray: _*
      )
      .withExposedPorts(containerPort)
      .withHostConfig(newHostConfig().withPortBindings(portBinding))
  }

}
