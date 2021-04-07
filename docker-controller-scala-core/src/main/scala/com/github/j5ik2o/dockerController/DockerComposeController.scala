package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ AccessMode, Bind, SELContext, Volume }
import org.seasar.util.io.ResourceUtil

import java.io.File
import scala.concurrent.duration.{ DurationInt, FiniteDuration }

class DockerComposeController(dockerClient: DockerClient, outputFrameInterval: FiniteDuration = 500.millis)(
    val ymlPath: String
) extends DockerControllerImpl(dockerClient, outputFrameInterval)("docker/compose", Some("1.24.1")) {
  val baseDir: File = ResourceUtil.getBuildDir(ymlPath)

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val ymlFile    = ResourceUtil.getResourceAsFile(ymlPath)
    val bind       = new Bind(baseDir.getPath, Volume.parse(baseDir.getPath))
    val systemBind = new Bind("/var/run/docker.sock", Volume.parse("/docker.sock"), AccessMode.rw, SELContext.none)
    logger.debug(s"ymlFile = ${ymlFile.getName}")
    super
      .newCreateContainerCmd()
      .withCmd("up")
      .withEnv(s"COMPOSE_FILE=${ymlFile.getName}", "DOCKER_HOST=unix:///docker.sock")
      .withWorkingDir(baseDir.getPath)
      .withHostConfig(
        newHostConfig()
          .withBinds(bind, systemBind)
      )
  }

}
