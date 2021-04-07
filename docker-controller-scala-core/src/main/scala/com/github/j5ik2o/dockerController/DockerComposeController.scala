package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ AccessMode, Bind, SELContext, Volume }
import org.seasar.util.io.ResourceUtil

import java.io.File
import java.nio.file.Files
import scala.concurrent.duration.{ DurationInt, FiniteDuration }

class DockerComposeController(dockerClient: DockerClient, outputFrameInterval: FiniteDuration = 500.millis)(
    val dockerComposeWorkingDir: File,
    val ymlResourceName: String,
    val context: Map[String, AnyRef]
) extends DockerControllerImpl(dockerClient, outputFrameInterval)("docker/compose", Some("1.24.1")) {

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val ymlFile = if (ymlResourceName.endsWith(".ftl")) {
      if (!dockerComposeWorkingDir.exists()) dockerComposeWorkingDir.mkdir()
      val file = Files.createTempFile(dockerComposeWorkingDir.toPath, "docker-compose-", ".yml").toFile
      DockerComposeYmlGen.generate(ymlResourceName, context, file)
      file
    } else
      ResourceUtil.getResourceAsFile(ymlResourceName)

    val baseDir    = ymlFile.getParentFile
    val bind       = new Bind(baseDir.getPath, new Volume(baseDir.getPath), AccessMode.ro, SELContext.none)
    val systemBind = new Bind("/var/run/docker.sock", new Volume("/docker.sock"), AccessMode.rw, SELContext.none)
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
