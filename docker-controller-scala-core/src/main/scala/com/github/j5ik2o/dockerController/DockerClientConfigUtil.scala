package com.github.j5ik2o.dockerController

import com.github.dockerjava.core.{ DefaultDockerClientConfig, DockerClientConfig }
import org.slf4j.LoggerFactory

import scala.util.{ Failure, Success }

object DockerClientConfigUtil {
  private val logger = LoggerFactory.getLogger(getClass)

  def buildConfigAwareOfDockerMachine(
      configBuilder: DefaultDockerClientConfig.Builder = DefaultDockerClientConfig.createDefaultConfigBuilder,
      profileName: String = "default"
  ): DockerClientConfig = {
    DockerMachineEnv
      .load(profileName) match {
      case Success(env) =>
        logger.debug(s"env = $env")
        configBuilder
          .withDockerTlsVerify(env.tlsVerify)
          .withDockerHost(env.dockerHost)
          .withDockerCertPath(env.dockerCertPath)
          .build
      case Failure(ex) =>
        configBuilder.build()
    }
  }
}
