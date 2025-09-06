package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ AccessMode, Bind, SELContext, Volume }
import org.apache.commons.io.FileUtils
import org.seasar.util.io.ResourceUtil

import java.io.File
import scala.concurrent.duration.{ DurationInt, FiniteDuration }

object DockerComposeController {

  def apply(
      dockerClient: DockerClient,
      isDockerClientAutoClose: Boolean = false,
      outputFrameInterval: FiniteDuration = 500.millis
  )(
      dockerComposeWorkingDir: File,
      ymlResourceName: String,
      environmentNames: Seq[String],
      context: Map[String, AnyRef]
  ): DockerController =
    new DockerComposeController(dockerClient, isDockerClientAutoClose, outputFrameInterval)(
      dockerComposeWorkingDir,
      ymlResourceName,
      environmentNames,
      context
    )
}

private[dockerController] class DockerComposeController(
    dockerClient: DockerClient,
    isDockerClientAutoClose: Boolean,
    outputFrameInterval: FiniteDuration = 500.millis
)(
    val dockerComposeWorkingDir: File,
    val ymlResourceName: String,
    val environmentResourceNames: Seq[String],
    val context: Map[String, AnyRef]
) extends DockerControllerImpl(dockerClient, isDockerClientAutoClose, outputFrameInterval)(
      "docker/compose",
      Some("1.24.1")
    ) {

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val id = Base58.randomString(16)
    if (!dockerComposeWorkingDir.exists()) dockerComposeWorkingDir.mkdir()
    val ymlFile = if (ymlResourceName.endsWith(".ftl")) {
      val file = new File(dockerComposeWorkingDir, s"docker-compose-$id.yml")
      DockerComposeFileGen.generate(ymlResourceName, context + ("id" -> id), file)
      file
    } else {
      val srcFile  = ResourceUtil.getResourceAsFile(ymlResourceName)
      val destFile = new File(dockerComposeWorkingDir, srcFile.getName)
      FileUtils.copyFile(srcFile, destFile)
      destFile
    }

    environmentResourceNames.foreach { environmentResourceName =>
      if (environmentResourceName.endsWith(".ftl")) {
        val Array(base, ext, _) = environmentResourceName.split("\\.")
        val file                = new File(dockerComposeWorkingDir, s"$base-$id.$ext")
        DockerComposeFileGen.generate(environmentResourceName, context, file)
      } else {
        val srcFile  = ResourceUtil.getResourceAsFile(environmentResourceName)
        val destFile = new File(dockerComposeWorkingDir, srcFile.getName)
        FileUtils.copyFile(srcFile, destFile)
      }
    }

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
