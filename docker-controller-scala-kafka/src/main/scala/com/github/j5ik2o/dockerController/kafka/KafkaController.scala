package com.github.j5ik2o.dockerController.kafka

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command._
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Frame, Ports }
import com.github.j5ik2o.dockerController.WaitPredicates.WaitPredicate
import com.github.j5ik2o.dockerController.kafka.KafkaController._
import com.github.j5ik2o.dockerController.zooKeeper.ZooKeeperController
import com.github.j5ik2o.dockerController._

import java.util.UUID
import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration }
import scala.util.matching.Regex

object KafkaController {
  final val DefaultImageName: String        = "wurstmeister/kafka"
  final val DefaultImageTag: Option[String] = Some("2.13-2.6.0")
  final val RegexForWaitPredicate: Regex    = """.*\[KafkaServer id=\d\] started.*""".r

  def apply(
      dockerClient: DockerClient,
      outputFrameInterval: FiniteDuration = 500.millis,
      imageName: String = DefaultImageName,
      imageTag: Option[String] = DefaultImageTag,
      envVars: Map[String, String] = Map.empty
  )(
      kafkaExternalHostName: String,
      kafkaExternalHostPort: Int,
      createTopics: Seq[String] = Seq.empty
  ): KafkaController =
    new KafkaController(dockerClient, outputFrameInterval, imageName, imageTag, envVars)(
      kafkaExternalHostName,
      kafkaExternalHostPort,
      createTopics
    )
}

class KafkaController(
    dockerClient: DockerClient,
    outputFrameInterval: FiniteDuration = 500.millis,
    imageName: String = DefaultImageName,
    imageTag: Option[String] = DefaultImageTag,
    envVars: Map[String, String] = Map.empty
)(
    kafkaExternalHostName: String,
    kafkaExternalHostPort: Int,
    createTopics: Seq[String]
) extends DockerControllerImpl(dockerClient, outputFrameInterval)(imageName, imageTag) {
  val networkId: String = dockerClient.createNetworkCmd().withName("kafka-" + UUID.randomUUID().toString).exec().getId

  val kafkaNetwork: Network    = Network(networkId)
  val zkAlias: NetworkAlias    = NetworkAlias(kafkaNetwork, "zk1")
  val kafkaAlias: NetworkAlias = NetworkAlias(kafkaNetwork, "kafka1")

  val zooKeeperHostPort: Int = RandomPortUtil.temporaryServerPort()

  val zooKeeperController: ZooKeeperController = ZooKeeperController(dockerClient)(
    myId = 1,
    hostPort = zooKeeperHostPort,
    containerPort = zooKeeperHostPort, // ZooKeeperController.DefaultZooPort,
    networkAlias = Some(zkAlias)
  )

  protected val zooKeeperWaitPredicate: WaitPredicate =
    WaitPredicates.forLogMessageByRegex(ZooKeeperController.RegexForWaitPredicate)

  private val kafkaContainerName     = kafkaAlias.name
  private val zooKeeperContainerName = zkAlias.name
  private val zooKeeperContainerPort = zooKeeperController.containerPort

  private val environmentVariables = Map(
    "KAFKA_AUTO_CREATE_TOPICS_ENABLE" -> (if (createTopics.isEmpty) "false" else "true"),
    "KAFKA_CREATE_TOPICS"             -> createTopics.mkString(","),
    "KAFKA_BROKER_ID"                 -> "1",
    "KAFKA_ADVERTISED_LISTENERS" -> s"LISTENER_DOCKER_INTERNAL://$kafkaContainerName:19092,LISTENER_DOCKER_EXTERNAL://$kafkaExternalHostName:$kafkaExternalHostPort",
    "KAFKA_LISTENERS" -> s"LISTENER_DOCKER_INTERNAL://:19092,LISTENER_DOCKER_EXTERNAL://:$kafkaExternalHostPort",
    "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP" -> "LISTENER_DOCKER_INTERNAL:PLAINTEXT,LISTENER_DOCKER_EXTERNAL:PLAINTEXT",
    "KAFKA_INTER_BROKER_LISTENER_NAME"     -> "LISTENER_DOCKER_INTERNAL",
    "KAFKA_ZOOKEEPER_CONNECT"              -> s"$zooKeeperContainerName:$zooKeeperContainerPort",
    "KAFKA_LOG4J_LOGGERS" -> "kafka.controller=INFO,kafka.producer.async.DefaultEventHandler=INFO,state.change.logger=INFO",
    "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" -> "1"
  ) ++ envVars

  override def createContainer(f: CreateContainerCmd => CreateContainerCmd): CreateContainerResponse = {
    zooKeeperController.pullImageIfNotExists()
    zooKeeperController.createContainer()
    super.createContainer(f)
  }

  override def startContainer(f: StartContainerCmd => StartContainerCmd): Unit = {
    zooKeeperController.startContainer()
    super.startContainer(f)
  }

  override def stopContainer(f: StopContainerCmd => StopContainerCmd): Unit = {
    super.stopContainer(f)
    zooKeeperController.stopContainer()
  }

  override def removeContainer(f: RemoveContainerCmd => RemoveContainerCmd): Unit = {
    super.removeContainer(f)
    zooKeeperController.removeContainer()
    dockerClient.removeNetworkCmd(networkId).exec()
  }

  override def awaitCondition(duration: Duration)(predicate: Option[Frame] => Boolean): Unit = {
    zooKeeperController.awaitCondition(duration)(zooKeeperWaitPredicate)
    super.awaitCondition(duration)(predicate)
  }

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val containerPort = ExposedPort.tcp(kafkaExternalHostPort)
    val portBinding   = new Ports()
    portBinding.bind(containerPort, Ports.Binding.bindPort(kafkaExternalHostPort))
    val hostConfig = newHostConfig().withPortBindings(portBinding).withNetworkMode(kafkaAlias.network.id)
    super
      .newCreateContainerCmd()
      .withEnv(environmentVariables.map { case (k, v) => s"$k=$v" }.toArray: _*)
      .withExposedPorts(containerPort)
      .withHostConfig(hostConfig).withAliases(kafkaAlias.name)
  }

}
