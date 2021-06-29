package com.github.j5ik2o.dockerController.flyway

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ Bind, HostConfig, Mount, Volume }
import com.github.j5ik2o.dockerController.DockerControllerImpl
import com.github.j5ik2o.dockerController.flyway.FlywayController._
import org.apache.commons.io.FileUtils
import org.seasar.util.io.ResourceUtil

import java.io.File
import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.jdk.CollectionConverters._

object FlywayController {
  final val DefaultImageName: String        = "flyway/flyway"
  final val DefaultImageTag: Option[String] = Some("7")
}

class FlywayController(
    dockerClient: DockerClient,
    outputFrameInterval: FiniteDuration = 500.millis,
    imageName: String = DefaultImageName,
    imageTag: Option[String] = DefaultImageTag,
    envVars: Map[String, String] = Map.empty
)(val dockerWorkingDir: File, val flywayConfResourceName: String)
    extends DockerControllerImpl(dockerClient, outputFrameInterval)(imageName, imageTag) {

  private val environmentVariables = Map(
    "FLYWAY_EDITION" -> "community"
  ) ++
    envVars

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val conf     = new Volume("/flyway/conf")
    val srcFile  = ResourceUtil.getResourceAsFile(flywayConfResourceName)
    val destFile = new File(dockerWorkingDir, conf.getPath)
    FileUtils.copyFile(srcFile, destFile)
    val confPath    = destFile.getPath
    val confBind    = new Bind(confPath, conf)
    val drivers     = new Volume("/flyway/drivers")
    val driversPath = new File(dockerWorkingDir, drivers.getPath).getPath
    val driversBind = new Bind(driversPath, drivers)
    val sql         = new Volume("/flyway/sql")
    val sqlPath     = new File(dockerWorkingDir, drivers.getPath).getPath
    val sqlBind     = new Bind(sqlPath, sql)
    val jars        = new Volume("/flyway/jars")
    val jarsPath    = new File(dockerWorkingDir, drivers.getPath).getPath
    val jarsBind    = new Bind(jarsPath, jars)

    super
      .newCreateContainerCmd()
      .withCmd("migrate")
      .withEnv(environmentVariables.map { case (k, v) => s"$k=$v" }.toArray: _*)
      .withVolumes(conf, drivers, sql, jars)
      .withHostConfig(HostConfig.newHostConfig().withBinds(confBind, driversBind, sqlBind, jarsBind))
  }

}
