# docker-controller-scala

[![Actions Status: CI](https://github.com/j5ik2o/docker-controller-scala/workflows/CI/badge.svg)](https://github.com/j5ik2o/docker-controller-scala/actions?query=workflow%3A"CI")
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.j5ik2o/docker-controller-scala-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.j5ik2o/docker-controller-scala-core_2.13)
[![Renovate](https://img.shields.io/badge/renovate-enabled-brightgreen.svg)](https://renovatebot.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Tokei](https://tokei.rs/b1/github/j5ik2o/event-store-adapter-scala)](https://github.com/XAMPPRocky/tokei)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fj5ik2o%2Fdocker-controller-scala.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fj5ik2o%2Fdocker-controller-scala?ref=badge_shield)

This library provides an easy and simple way to handle Docker Container or Docker Compose on ScalaTest, based on [docker-java](https://github.com/docker-java/docker-java). The implementation of this library is thin, and if you know [docker-java](https://github.com/docker-java/docker-java), your learning cost will be negligible.

M1 Macs also be supported.

## Installation

Add the following to your sbt build (2.12.x, 2.13.x, 3.0.x):

```scala
val version = "..."

libraryDependencies += Seq(
  "io.github.j5ik2o" %% "docker-controller-scala-core" % version,
  "io.github.j5ik2o" %% "docker-controller-scala-scalatest" % version, // for scalatest
  // RDB
  "io.github.j5ik2o" %% "docker-controller-scala-mysql" % version, // optional
  "io.github.j5ik2o" %% "docker-controller-scala-postgresql" % version, // optional
  "io.github.j5ik2o" %% "docker-controller-scala-flyway" % version, // optional
  // NoSQL
  "io.github.j5ik2o" %% "docker-controller-scala-memcached" % version, // optional
  "io.github.j5ik2o" %% "docker-controller-scala-redis" % version, // optional
  "io.github.j5ik2o" %% "docker-controller-scala-elasticsearch" % version, // optional
  // Kafka
  "io.github.j5ik2o" %% "docker-controller-scala-zookeeper" % version, // optional
  "io.github.j5ik2o" %% "docker-controller-scala-kafka" % version, // optional
  // AWS Services
  "io.github.j5ik2o" %% "docker-controller-scala-dynamodb-local" % version, // optional
  "io.github.j5ik2o" %% "docker-controller-scala-minio" % version, // optional
  "io.github.j5ik2o" %% "docker-controller-scala-localstack" % version, // optional
  "io.github.j5ik2o" %% "docker-controller-scala-elasticmq" % version, // optional
)
```

In most cases, you can just select the scalatest module and the module you need.

```scala
libraryDependencies += Seq(
  "io.github.j5ik2o" %% "docker-controller-scala-scalatest" % version,
  "io.github.j5ik2o" %% "docker-controller-scala-mysql" % version,
)
```

## Usage

[DockerController](docker-controller-scala-core/src/main/scala/com/github/j5ik2o/dockerController/DockerController.scala) that the thin wrapper for [docker-java](https://github.com/docker-java/docker-java) controls Docker Image and Docker Container for testing.

### How to test with preset DockerController

The `DockerController` for the corresponding preset is as follows. Please see the corresponding `**Spec` for specific usage.

- RDBMS
  - [MySQLController](docker-controller-scala-mysql/src/main/scala/com/github/j5ik2o/dockerController/mysql/MySQLController.scala) / [MySQLControllerSpec](docker-controller-scala-mysql/src/test/scala/com/github/j5ik2o/dockerController/MySQLControllerSpec.scala)
  - [PostgreSQLController](docker-controller-scala-postgresql/src/main/scala/com/github/j5ik2o/dockerController/postgresql/PostgreSQLController.scala) / [PostgreSQLControllerSpec](docker-controller-scala-postgresql/src/test/scala/com/github/j5ik2o/dockerController/postgresql/PostgreSQLControllerSpec.scala)
- NoSQL
  - [MemcachedController](docker-controller-scala-memcached/src/main/scala/com/github/j5ik2o/dockerController/memcached/MemcachedController.scala) / [MemcachedControllerSpec](docker-controller-scala-memcached/src/test/scala/com/github/j5ik2o/dockerController/memcached/MemcachedControllerSpec.scala)
  - [RedisController](docker-controller-scala-redis/src/main/scala/com/github/j5ik2o/dockerController/redis/RedisController.scala) / [RedisControllerSpec](docker-controller-scala-redis/src/test/scala/com/github/j5ik2o/dockerController/redis/RedisControllerSpec.scala)
  - [ElasticsearchController](docker-controller-scala-elasticsearch/src/main/scala/com/github/j5ik2o/dockerController/elasticsearch/ElasticsearchController.scala) / [ElasticsearchControllerSpec](docker-controller-scala-elasticsearch/src/test/scala/com/github/j5ik2o/dockerController/elasticsearch/ElasticsearchControllerSpec.scala)
  - [ZooKeeperController](docker-controller-scala-zookeeper/src/main/scala/com/github/j5ik2o/dockerController/zooKeeper/ZooKeeperController.scala) / [ZooKeeperControllerSpec](docker-controller-scala-zookeeper/src/test/scala/com/github/j5ik2o/dockerController/ZooKeeperControllerSpec.scala)
  - [KafkaController](docker-controller-scala-kafka/src/main/scala/com/github/j5ik2o/dockerController/kafka/KafkaController.scala) / [KafkaControllerSpec](docker-controller-scala-kafka/src/test/scala/com/github/j5ik2o/dockerController/kafka/KafkaControllerSpec.scala)
- AWS Storages
  - [LocalStackController](docker-controller-scala-localstack/src/main/scala/com/github/j5ik2o/dockerController/localstack/LocalStackController.scala) / [LocalStackControllerSpec](docker-controller-scala-localstack/src/test/scala/com/github/j5ik2o/dockerController/localstack/LocalStackControllerSpec.scala)
  - [DynamoDBLocalController](docker-controller-scala-dynamodb-local/src/main/scala/com/github/j5ik2o/dockerController/dynamodbLocal/DynamoDBLocalController.scala) / [DynamoDBLocalControllerSpec](docker-controller-scala-dynamodb-local/src/test/scala/com/github/j5ik2o/dockerController/dynamodbLocal/DynamoDBLocalControllerSpec.scala)
  - [MinioController](docker-controller-scala-minio/src/main/scala/com/github/j5ik2o/dockerController/minio/MinioController.scala) / [MinioControllerSpec](docker-controller-scala-minio/src/test/scala/com/github/j5ik2o/dockerController/minio/MinioControllerSpec.scala)
  - [ElasticMQController](docker-controller-scala-elasticmq/src/main/scala/com/github/j5ik2o/dockerController/elasticmq/ElasticMQController.scala) / [ElasticMQControllerSpec](docker-controller-scala-elasticmq/src/test/scala/com/github/j5ik2o/dockerController/elasticmq/ElasticMQControllerSpec.scala)

### Use Flyway Migrate Command on MySQL/PostgreSQL

If you'd like to use `flyway` module, you can use `docker-controller-scala-flyway`.

```scala
libraryDependencies += Seq(
  "io.github.j5ik2o" %% "docker-controller-scala-scalatest" % version,
  "io.github.j5ik2o" %% "docker-controller-scala-mysql" % version,
  "io.github.j5ik2o" %% "docker-controller-scala-flyway" % version, // for flyway
)
```

Mix-in `FlywaySpecSupport` then, put the sql files in `src/resources/flyway`(`src/resources/**` can be set to any string.), run `flywayContext.flyway.migrate()` in `afterStartContainers` method.

### How to test with DockerController your customized

To launch a docker container for testing

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
    val hostPort: Int              = temporaryServerPort()
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

### How to use Docker Compose

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
    Seq.empty,
    Map("nginxHostPort" -> hostPort.toString)
  )

  override val dockerControllers: Vector[DockerController] = {
    Vector(dockerController)
  }
// ...
}     
```


### Use legacy Compose with a newer daemon

`DockerComposeController` uses `docker/compose:1.24.1`. If that Compose client
reports that its API version is too old, inspect the daemon's supported range.

```sh
docker version --format '{{.Server.MinAPIVersion}} {{.Server.APIVersion}}'
```

Choose an API version supported by the daemon and validate the Compose operations
your tests use. This legacy client does not gain support for every newer API
feature when you set its version. `1.40` worked with a daemon whose minimum was
`1.40`; it is not a universal default.

For the `dockerController` created above, register the hook before
`createContainer()`. Keep `COMPOSE_FILE`, `DOCKER_HOST`, and any other environment
entries while replacing only `COMPOSE_API_VERSION`.

```scala
val composeApiVersion = "1.40" // Use a value validated for your daemon.

dockerController.configureCreateContainerCmd { cmd =>
	val env = Option(cmd.getEnv).toSeq.flatten
		.filterNot(_.startsWith("COMPOSE_API_VERSION="))
	cmd.withEnv((env :+ s"COMPOSE_API_VERSION=$composeApiVersion"): _*)
}
```

Passing only the new entry to `withEnv` replaces the controller's environment.
`COMPOSE_API_VERSION` applies to this legacy Compose client. Do not use this example as Compose V2 configuration.

## License
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fj5ik2o%2Fdocker-controller-scala.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2Fj5ik2o%2Fdocker-controller-scala?ref=badge_large)
