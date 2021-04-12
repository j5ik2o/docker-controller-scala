package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.{ DockerClientConfig, DockerClientImpl }
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import com.github.j5ik2o.dockerController.WaitPredicates.WaitPredicate
import org.scalatest.{ Suite, SuiteMixin }
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.duration.Duration

trait DockerControllerSuiteBase extends SuiteMixin { this: Suite =>

  case class WaitPredicateSetting(awaitDuration: Duration, waitPredicate: WaitPredicate)

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  protected val dockerClientConfig: DockerClientConfig = DockerClientConfigUtil.buildConfigAwareOfDockerMachine()

  protected val dockerHost: String = DockerClientConfigUtil.dockerHost(dockerClientConfig)

  protected val dockerHttpClient: DockerHttpClient = new ApacheDockerHttpClient.Builder()
    .dockerHost(dockerClientConfig.getDockerHost)
    .sslConfig(dockerClientConfig.getSSLConfig)
    .build()

  protected val dockerClient: DockerClient = DockerClientImpl.getInstance(dockerClientConfig, dockerHttpClient)

  protected val dockerControllers: Vector[DockerController]

  protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting]

  protected def createDockerContainer(
      dockerController: DockerController,
      testName: Option[String]
  ): DockerController = {
    logger.debug(s"createDockerContainer --- $testName")
    val result = dockerController.pullImageIfNotExists().createContainer()
    afterDockerContainerCreated(dockerController, testName)
    result
  }

  protected def startDockerContainer(dockerController: DockerController, testName: Option[String]): DockerController = {
    logger.debug(s"startDockerContainer --- $testName")
    val waitPredicate = waitPredicatesSettings(dockerController)
    val result = dockerController
      .startContainer()
      .awaitCondition(waitPredicate.awaitDuration)(waitPredicate.waitPredicate)
    afterDocketContainerStarted(dockerController, testName)
    result
  }

  protected def stopDockerContainer(dockerController: DockerController, testName: Option[String]): DockerController = {
    logger.debug(s"stopDockerContainer --- $testName")
    beforeDockerContainerStopped(dockerController, testName)
    dockerController.stopContainer()
  }

  protected def removeDockerContainer(
      dockerController: DockerController,
      testName: Option[String]
  ): DockerController = {
    logger.debug(s"removeDockerContainer --- $testName")
    beforeDockerContainerRemoved(dockerController, testName)
    dockerController.removeContainer()
  }

  protected def afterDockerContainerCreated(dockerController: DockerController, testName: Option[String]): Unit = {
    logger.debug(s"afterDockerContainerCreated --- $testName")
  }

  protected def beforeDockerContainerRemoved(dockerController: DockerController, testName: Option[String]): Unit = {
    logger.debug(s"beforeDockerContainerRemoved --- $testName")
  }

  protected def afterDocketContainerStarted(dockerController: DockerController, testName: Option[String]): Unit = {
    logger.debug(s"afterDocketContainerStarted --- $testName")
  }

  protected def beforeDockerContainerStopped(dockerController: DockerController, testName: Option[String]): Unit = {
    logger.debug(s"beforeDockerContainerStopped --- $testName")
  }

}
