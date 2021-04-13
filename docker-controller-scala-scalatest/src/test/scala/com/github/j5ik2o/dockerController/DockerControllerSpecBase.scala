package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.NetworkSettingsImplicits._
import org.scalatest.freespec.AnyFreeSpec

import java.net.URL
import scala.concurrent.duration._

abstract class DockerControllerSpecBase extends AnyFreeSpec with DockerControllerSpecSupport {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt

  val nginx: DockerController = DockerController(dockerClient)(
    imageName = "nginx",
    tag = Some("latest")
  ).configureCreateContainerCmd { cmd =>
    val hostPort: Int              = RandomPortUtil.temporaryServerPort()
    val containerPort: ExposedPort = ExposedPort.tcp(80)
    val portBinding: Ports         = new Ports()
    portBinding.bind(containerPort, Ports.Binding.bindPort(hostPort))
    logger.debug(s"hostPort = $hostPort, containerPort = $containerPort")
    cmd
      .withExposedPorts(containerPort)
      .withHostConfig(newHostConfig().withPortBindings(portBinding))
  }

  override val dockerControllers: Vector[DockerController] = {
    Vector(nginx)
  }

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] =
    Map(
      nginx -> WaitPredicateSetting(
        Duration.Inf,
        WaitPredicates
          .forLogMessageContained("Configuration complete; ready for start up", Some((1 * testTimeFactor).seconds))
      )
    )

  getClass.getSimpleName.stripPrefix("DockerController_").stripSuffix("_Spec") - {
    "run-1" in {
      val hostPort = nginx.inspectContainer().getNetworkSettings.bindingHostPort(ExposedPort.tcp(80)).get
      val url      = new URL(s"http://$dockerHost:$hostPort")
      HttpRequestUtil.wget(url)
    }
    "run-2" in {
      val hostPort = nginx.inspectContainer().getNetworkSettings.bindingHostPort(ExposedPort.tcp(80)).get
      val url      = new URL(s"http://$dockerHost:$hostPort")
      HttpRequestUtil.wget(url)
    }
  }
}
