# docker-controller-scala

[![CircleCI](https://circleci.com/gh/j5ik2o/docker-controller-scala/tree/main.svg?style=shield)](https://circleci.com/gh/j5ik2o/docker-controller-scala/tree/main)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.j5ik2o/docker-controller-scala-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.j5ik2o/docker-controller-scala-core_2.13)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

This library provides an easy and simple way to handle Docker Container or Docker Compose on ScalaTest, based on [docker-java](https://github.com/docker-java/docker-java). The implementation of this library is thin, and if you know [docker-java](https://github.com/docker-java/docker-java), your learning cost will be negligible.

## Installation

Add the following to your sbt build (2.12.x, 2.13.x):

```scala
resolvers += "Sonatype OSS Release Repository" at "https://oss.sonatype.org/content/repositories/releases/"

val version = "..."

libraryDependencies += Seq(
  "com.github.j5ik2o" %% "docker-controller-scala-core" % version,
  "com.github.j5ik2o" %% "docker-controller-scala-scalatest" % version
)
```

## Usage

To launch a Docker container for testing

```scala
// In ScalaTest, please mix-in DockerControllerSpecSupport.
class NginxSpec extends AnyFreeSpec with DockerControllerSpecSupport {
  
  // choose whether to create and destroy containers per test class (ForAllTest) or per test (ForEachTest).
  override def createRemoveLifecycle: DockerContainerCreateRemoveLifecycle.Value =
    DockerContainerCreateRemoveLifecycle.ForEachTest

  // choose whether to start and stop containers per test class (ForAllTest) or per test (ForEachTest).
  override def startStopLifecycle: DockerContainerStartStopLifecycle.Value =
    DockerContainerStartStopLifecycle.ForEachTest
    
  val nginx: DockerController = new DockerControllerImpl(dockerClient)(
    imageName = "nginx",
    tag = Some("latest")
  ) {

    // if customize the container generation, please do the following.
    // In this example, a random host port is specified.
    override protected def newCreateContainerCmd(): CreateContainerCmd = {
      val hostPort: Int              = RandomPortUtil.temporaryServerPort()
      val containerPort: ExposedPort = ExposedPort.tcp(80)
      val portBinding: Ports         = new Ports()
      portBinding.bind(containerPort, Ports.Binding.bindPort(hostPort))
      logger.debug(s"hostPort = $hostPort, containerPort = $containerPort")
      super
        .newCreateContainerCmd()
        .withExposedPorts(containerPort)
        .withHostConfig(newHostConfig().withPortBindings(portBinding))
    }
  }

  // Specify DockerControllers to be launched.
  override val dockerControllers: Vector[DockerController] = {
    Vector(nginx)
  }

　// if customize the container startup, please do the following.
  override protected def startDockerContainer(
      dockerController: DockerController,
      testName: Option[String]
  ): DockerController = {
    require(dockerController == nginx)
    val result = super
      .startDockerContainer(dockerController, testName)
      .awaitCondition(Duration.Inf)(_.toString.contains("Configuration complete; ready for start up"))
    Thread.sleep(1000)
    result
  }

  "nginx" - {
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
```

If use Docker Compose

- Place the `docker-compose.yml.ftl`(ftl is Freemarker template) in `src/test/resources`. `docker-compose.yml.ftl` can be renamed to anything you want.
- The variables in the ftl can be freely determined.

```yaml
version: '3'
services:
  nginx:
    image: nginx
    ports:
      - ${nginxHostPort}:80
```

- Use `DockerComposeController`, which is a subtype of `DockerController`. Other than this, it is the same as the test method above.
- Pass the context containing the values of the variables to be used in the FTL to the constructor of `DockerComposeController`.

```scala
class NginxSpec extends AnyFreeSpec with DockerControllerSpecSupport {
// ...
  val buildDir: File                = ResourceUtil.getBuildDir(getClass)
  val dockerComposeWorkingDir: File = new File(buildDir, "docker-compose")
  val dockerController = new DockerComposeController(dockerClient)(
    dockerComposeWorkingDir,
    "docker-compose.yml.ftl",
    Map("nginxHostPort" -> hostPort.toString)
  )

  override val dockerControllers: Vector[DockerController] = {
    Vector(dockerController)
  }
// ...
}     
```
