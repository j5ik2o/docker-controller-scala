package com.github.j5ik2o.dockerController

import com.github.dockerjava.core.{ DefaultDockerClientConfig, DockerClientConfig }
import org.slf4j.LoggerFactory

import scala.util.{ Failure, Success }

object DockerClientConfigUtil {
  private val logger = LoggerFactory.getLogger(getClass)

  def dockerHost(dockerClientConfig: DockerClientConfig): String =
    if (dockerClientConfig.getDockerHost.getHost == null)
      "127.0.0.1"
    else
      dockerClientConfig.getDockerHost.getHost

  def buildConfigAwareOfDockerMachine(
      configBuilder: DefaultDockerClientConfig.Builder = DefaultDockerClientConfig.createDefaultConfigBuilder,
      profileName: String = "default"
  ): DockerClientConfig = {
    if (DockerMachineEnv.isSupportDockerMachine) {
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
          logger.warn(
            s"Failed to load `docker-machine env $profileName`, so it was fallback to the default configuration.",
            ex
          )
          // Let docker-java handle the default configuration
          configBuilder.build()
      }
    } else {
      // Let docker-java handle the default configuration
      logger.debug("Using docker-java default configuration")
      configBuilder.build()
    }
  }
}
