package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.{ DockerClientConfig, DockerClientImpl }
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import com.github.j5ik2o.dockerController.WaitPredicates.WaitPredicate
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.duration.Duration

trait DockerControllerHelper {

  case class WaitPredicateSetting(awaitDuration: Duration, waitPredicate: WaitPredicate)

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  protected val dockerClientConfig: DockerClientConfig = DockerClientConfigUtil.buildConfigAwareOfDockerMachine()

  protected val dockerHost: String = DockerClientConfigUtil.dockerHost(dockerClientConfig)

  protected val dockerHttpClient: DockerHttpClient = new ApacheDockerHttpClient.Builder()
    .dockerHost(dockerClientConfig.getDockerHost)
    .sslConfig(dockerClientConfig.getSSLConfig)
    .build()

  protected val dockerClient: DockerClient = DockerClientImpl.getInstance(dockerClientConfig, dockerHttpClient)

  protected def dockerControllers: Vector[DockerController]

  protected def waitPredicatesSettings: Map[DockerController, WaitPredicateSetting]

  protected def createDockerContainer(
      dockerController: DockerController,
      testName: Option[String]
  ): Unit = {
    logger.debug(s"createDockerContainer --- $testName")
    dockerController.pullImageIfNotExists()
    beforeDockerContainerCreate(dockerController, testName)
    dockerController.createContainer()
    afterDockerContainerCreated(dockerController, testName)
  }

  protected def startDockerContainer(dockerController: DockerController, testName: Option[String]): Unit = {
    logger.debug(s"startDockerContainer --- $testName")
    beforeDocketContainerStart(dockerController, testName)
    dockerController.startContainer()
    val waitPredicateOpt = waitPredicatesSettings.get(dockerController)
    waitPredicateOpt.foreach { waitPredicate =>
      dockerController.awaitCondition(waitPredicate.awaitDuration)(waitPredicate.waitPredicate)
    }
    afterDocketContainerStarted(dockerController, testName)
  }

  protected def stopDockerContainer(dockerController: DockerController, testName: Option[String]): Unit = {
    logger.debug(s"stopDockerContainer --- $testName")
    beforeDockerContainerStop(dockerController, testName)
    dockerController.stopContainer()
    afterDockerContainerStopped(dockerController, testName)
  }

  protected def removeDockerContainer(
      dockerController: DockerController,
      testName: Option[String]
  ): Unit = {
    logger.debug(s"removeDockerContainer --- $testName")
    beforeDockerContainerRemove(dockerController, testName)
    dockerController.removeContainer()
    afterDockerContainerRemoved(dockerController, testName)
  }

  protected def beforeDockerContainerCreate(dockerController: DockerController, testName: Option[String]): Unit = {
    logger.debug(s"beforeDockerContainerCreate --- $testName")
  }

  protected def afterDockerContainerCreated(dockerController: DockerController, testName: Option[String]): Unit = {
    logger.debug(s"afterDockerContainerCreated --- $testName")
  }

  protected def beforeDockerContainerRemove(dockerController: DockerController, testName: Option[String]): Unit = {
    logger.debug(s"beforeDockerContainerRemove --- $testName")
  }

  protected def afterDockerContainerRemoved(dockerController: DockerController, testName: Option[String]): Unit = {
    logger.debug(s"afterDockerContainerRemoved --- $testName")
  }

  protected def beforeDocketContainerStart(dockerController: DockerController, testName: Option[String]): Unit = {
    logger.debug(s"beforeDocketContainerStart --- $testName")
  }

  protected def afterDocketContainerStarted(dockerController: DockerController, testName: Option[String]): Unit = {
    logger.debug(s"afterDocketContainerStarted --- $testName")
  }

  protected def beforeDockerContainerStop(dockerController: DockerController, testName: Option[String]): Unit = {
    logger.debug(s"beforeDockerContainerStopped --- $testName")
  }

  protected def afterDockerContainerStopped(dockerController: DockerController, testName: Option[String]): Unit = {
    logger.debug(s"afterDockerContainerStopped --- $testName")
  }

}
