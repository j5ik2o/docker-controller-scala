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
  "com.github.j5ik2o" %% "docker-controller-scala-scalatest" % version, // for scalatest
  "com.github.j5ik2o" %% "docker-controller-scala-mysql" % version, // optional
  "com.github.j5ik2o" %% "docker-controller-scala-dynamodb-local" % version, // optional
  "com.github.j5ik2o" %% "docker-controller-scala-minio" % version, // optional
  "com.github.j5ik2o" %% "docker-controller-scala-kafka" % version, // optional
  "com.github.j5ik2o" %% "docker-controller-scala-elasticsearch" % version, // optional
)
```

## Usage

### How to test with preset DockerController

The DockerController for the corresponding preset is as follows.

- [MySQLController](docker-controller-scala-mysql/src/main/scala/com/github/j5ik2o/dockerController/mysql/MySQLController.scala)
- [DynamoDBLocalController](docker-controller-scala-dynamodb-local/src/main/scala/com/github/j5ik2o/dockerController/dynamodbLocal/DynamoDBLocalController.scala)
- [MinioController](docker-controller-scala-minio/src/main/scala/com/github/j5ik2o/dockerController/minio/MinioController.scala)
- [KafkaController](docker-controller-scala-kafka/src/main/scala/com/github/j5ik2o/dockerController/kafka/KafkaController.scala)
- [ZooKeeperController](docker-controller-scala-zookeeper/src/main/scala/com/github/j5ik2o/dockerController/zooKeeper/ZooKeeperController.scala)
- [ElasticsearchController](docker-controller-scala-elasticsearch/src/main/scala/com/github/j5ik2o/dockerController/elasticsearch/ElasticsearchController.scala)

```scala
class MySQLControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt
  logger.debug(s"testTimeFactor = $testTimeFactor")

  val hostPort: Int                                                  = RandomPortUtil.temporaryServerPort()
  val rootPassword: String                                           = "test"
  val controller: MySQLController                                    = MySQLController(dockerClient)(hostPort, rootPassword, databaseName = Some("test"))
  override protected val dockerControllers: Vector[DockerController] = Vector(controller)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] =
    Map(
      controller -> WaitPredicateSetting(
        Duration.Inf,
        WaitPredicates.forListeningHostTcpPort(
          dockerHost,
          hostPort,
          (1 * testTimeFactor).seconds,
          Some((3 * testTimeFactor).seconds)
        )
      )
    )

  "MySQLController" - {
    "run" in {
      var conn: Connection     = null
      var stmt: Statement      = null
      var resultSet: ResultSet = null
      try {
        Class.forName("com.mysql.cj.jdbc.Driver")
        conn = DriverManager.getConnection(
          s"jdbc:mysql://$dockerHost:$hostPort/test?user=root&password=$rootPassword"
        )
        stmt = conn.createStatement
        resultSet = stmt.executeQuery("SELECT 1 FROM DUAL")
        while (resultSet.next())
          assert(resultSet.getInt(1) == 1)
      } catch {
        case NonFatal(ex) =>
          fail("occurred error", ex)
      } finally {
        if (resultSet != null)
          resultSet.close()
        if (stmt != null)
          stmt.close()
        if (conn != null)
          conn.close()
      }
    }
  }
}

```

### How to test with DockerController your customized

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
    
  val nginx: DockerController = DockerController(dockerClient)(
    imageName = "nginx",
    tag = Some("latest")
  ).configureCreateContainerCmd { cmd =>
    // if customize the container generation, please do the following.
    // In this example, a random host port is specified.
    val hostPort: Int              = RandomPortUtil.temporaryServerPort()
    val containerPort: ExposedPort = ExposedPort.tcp(80)
    val portBinding: Ports         = new Ports()
    portBinding.bind(containerPort, Ports.Binding.bindPort(hostPort))
    logger.debug(s"hostPort = $hostPort, containerPort = $containerPort")
    cmd
      .withExposedPorts(containerPort)
      .withHostConfig(newHostConfig().withPortBindings(portBinding))
  }

  // Specify DockerControllers to be launched.
  override protected val dockerControllers: Vector[DockerController] = {
    Vector(nginx)
  }

  // Set the condition to wait for the container to be started.
  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] =
    Map(
      nginx -> WaitPredicateSetting(
        Duration.Inf,
        WaitPredicates.forLogMessageContained("Configuration complete; ready for start up")
      )
    )

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
  val dockerController = DockerComposeController(dockerClient)(
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
