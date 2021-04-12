package com.github.j5ik2o.dockerController.kafka

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.{ CreateContainerCmd, RemoveContainerCmd, StartContainerCmd, StopContainerCmd }
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.{
  DockerController,
  DockerControllerImpl,
  Network,
  NetworkAlias,
  RandomPortUtil,
  ZooKeeperController
}
import com.github.j5ik2o.dockerController.kafka.KafkaController._

import java.util.UUID
import scala.concurrent.duration.{ DurationInt, FiniteDuration }

object KafkaController {
  final val ImageName: String        = "wurstmeister/kafka"
  final val ImageTag: Option[String] = Some("2.13-2.6.0")
  final val RegexForWaitPredicate    = """.*\[KafkaServer id=1\] started.*""".r

  def apply(dockerClient: DockerClient, outputFrameInterval: FiniteDuration = 500.millis)(
      kafkaExternalHostName: String,
      kafkaExternalHostPort: Int,
      createTopics: Seq[String]
  ): KafkaController =
    new KafkaController(dockerClient, outputFrameInterval)(
      kafkaExternalHostName,
      kafkaExternalHostPort,
      createTopics
    )
}

class KafkaController(dockerClient: DockerClient, outputFrameInterval: FiniteDuration = 500.millis)(
    kafkaExternalHostName: String,
    kafkaExternalHostPort: Int,
    createTopics: Seq[String]
) extends DockerControllerImpl(dockerClient, outputFrameInterval)(ImageName, ImageTag) {
  val networkId: String = dockerClient.createNetworkCmd().withName("kafka-" + UUID.randomUUID().toString).exec().getId

  val kafkaNetwork: Network    = Network(networkId)
  val zkAlias: NetworkAlias    = NetworkAlias(kafkaNetwork, "zk1")
  val kafkaAlias: NetworkAlias = NetworkAlias(kafkaNetwork, "kafka1")

  val zooKeeperHostPort: Int = RandomPortUtil.temporaryServerPort()

  val zooKeeperController: ZooKeeperController = ZooKeeperController(dockerClient)(
    myId = 1,
    host = kafkaExternalHostName,
    hostPort = zooKeeperHostPort,
    containerPort = zooKeeperHostPort, // ZooKeeperController.DefaultZooPort,
    networkAlias = Some(zkAlias)
  )

  private val kafkaContainerName     = kafkaAlias.name
  private val zooKeeperContainerName = zkAlias.name
  private val zooKeeperContainerPort = zooKeeperController.containerPort

  private val env = Map(
    "KAFKA_AUTO_CREATE_TOPICS_ENABLE"        -> (if (createTopics.isEmpty) "false" else "true"),
    "KAFKA_CREATE_TOPICS"                    -> createTopics.mkString(","),
    "KAFKA_BROKER_ID"                        -> "1",
    "KAFKA_ADVERTISED_LISTENERS"             -> s"LISTENER_DOCKER_INTERNAL://$kafkaContainerName:19092,LISTENER_DOCKER_EXTERNAL://$kafkaExternalHostName:$kafkaExternalHostPort",
    "KAFKA_LISTENERS"                        -> s"LISTENER_DOCKER_INTERNAL://:19092,LISTENER_DOCKER_EXTERNAL://:$kafkaExternalHostPort",
    "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP"   -> "LISTENER_DOCKER_INTERNAL:PLAINTEXT,LISTENER_DOCKER_EXTERNAL:PLAINTEXT",
    "KAFKA_INTER_BROKER_LISTENER_NAME"       -> "LISTENER_DOCKER_INTERNAL",
    "KAFKA_ZOOKEEPER_CONNECT"                -> s"$zooKeeperContainerName:$zooKeeperContainerPort",
    "KAFKA_LOG4J_LOGGERS"                    -> "kafka.controller=INFO,kafka.producer.async.DefaultEventHandler=INFO,state.change.logger=INFO",
    "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" -> "1"
  )

  override def removeContainer(f: RemoveContainerCmd => RemoveContainerCmd): DockerController = {
    val result = super.removeContainer(f)
    dockerClient.removeNetworkCmd(networkId).exec()
    result
  }

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val containerPort = ExposedPort.tcp(kafkaExternalHostPort)
    val portBinding   = new Ports()
    portBinding.bind(containerPort, Ports.Binding.bindPort(kafkaExternalHostPort))
    val hostConfig = newHostConfig().withPortBindings(portBinding).withNetworkMode(kafkaAlias.network.id)
    super
      .newCreateContainerCmd()
      .withEnv(env.map { case (k, v) => s"$k=$v" }.toArray: _*)
      .withExposedPorts(containerPort)
      .withHostConfig(hostConfig).withAliases(kafkaAlias.name)
  }

}
