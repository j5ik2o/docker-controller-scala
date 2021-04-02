package com.github.j5ik2o.dockerController

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.j5ik2o.dockerController.DockerEnvLoader.load

object DockerClientConfigBuilder {

  def loadDockerClientConfig(
      configBuilder: DefaultDockerClientConfig.Builder = DefaultDockerClientConfig.createDefaultConfigBuilder
  ): DefaultDockerClientConfig = {
    load().fold(
      _ => configBuilder.build(), { env =>
        configBuilder
          .withDockerTlsVerify(env.tlsVerify)
          .withDockerHost(env.dockerHost)
          .withDockerCertPath(env.dockerCertPath)
          .build
      }
    )
  }
}
