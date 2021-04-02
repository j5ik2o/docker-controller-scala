package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.{ DockerClientConfig, DockerClientImpl }
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import org.scalatest.{ Suite, SuiteMixin }
import org.slf4j.{ Logger, LoggerFactory }

trait DockerControllerSuiteBase extends SuiteMixin { this: Suite =>

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  protected val dockerClientConfig: DockerClientConfig = DockerClientConfigUtil.buildConfigAwareOfDockerMachine()

  protected val dockerHost: String =
    if (dockerClientConfig.getDockerHost.getHost == null)
      "127.0.0.1"
    else
      dockerClientConfig.getDockerHost.getHost

  protected val dockerHttpClient: DockerHttpClient = new ApacheDockerHttpClient.Builder()
    .dockerHost(dockerClientConfig.getDockerHost)
    .sslConfig(dockerClientConfig.getSSLConfig)
    .build()

  protected val dockerClient: DockerClient = DockerClientImpl.getInstance(dockerClientConfig, dockerHttpClient)

  protected val dockerControllers: Vector[DockerController]

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
    val result = dockerController.startContainer()
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
