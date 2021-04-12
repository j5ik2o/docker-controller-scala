package com.github.j5ik2o.dockerController.kafka

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.{ DockerControllerImpl, Network }
import com.github.j5ik2o.dockerController.kafka.KafkaController._

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

object KafkaController {
  final val ImageName: String        = "wurstmeister/kafka"
  final val ImageTag: Option[String] = Some("2.13-2.6.0")
  final val DefaultContainerPort     = 9092
  final val RegexForWaitPredicate    = """.*\[KafkaServer id=1\] started.*""".r

  def apply(dockerClient: DockerClient, outputFrameInterval: FiniteDuration = 500.millis)(
      kafkaHostName: String,
      kafkaHostPort: Int,
      zooKeeperHostName: String,
      zooKeeperContainerPort: Int,
      network: Option[Network],
      createTopics: Seq[String]
  ): KafkaController =
    new KafkaController(dockerClient, outputFrameInterval)(
      kafkaHostName,
      kafkaHostPort,
      zooKeeperHostName,
      zooKeeperContainerPort,
      network,
      createTopics
    )
}

class KafkaController(dockerClient: DockerClient, outputFrameInterval: FiniteDuration = 500.millis)(
    kafkaHostName: String,
    kafkaHostPort: Int,
    zooKeeperHostName: String,
    zooKeeperContainerPort: Int,
    network: Option[Network],
    createTopics: Seq[String]
) extends DockerControllerImpl(dockerClient, outputFrameInterval)(ImageName, ImageTag) {

  val env = Map(
    "KAFKA_AUTO_CREATE_TOPICS_ENABLE"        -> (if (createTopics.isEmpty) "false" else "true"),
    "KAFKA_CREATE_TOPICS"                    -> createTopics.mkString(","),
    "KAFKA_BROKER_ID"                        -> "1",
    "KAFKA_ADVERTISED_LISTENERS"             -> s"LISTENER_DOCKER_INTERNAL://$kafkaHostName:19092,LISTENER_DOCKER_EXTERNAL://$kafkaHostName:$kafkaHostPort",
    "KAFKA_LISTENERS"                        -> s"LISTENER_DOCKER_INTERNAL://:19092,LISTENER_DOCKER_EXTERNAL://:$kafkaHostPort",
    "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP"   -> "LISTENER_DOCKER_INTERNAL:PLAINTEXT,LISTENER_DOCKER_EXTERNAL:PLAINTEXT",
    "KAFKA_INTER_BROKER_LISTENER_NAME"       -> "LISTENER_DOCKER_INTERNAL",
    "KAFKA_ZOOKEEPER_CONNECT"                -> s"$zooKeeperHostName:$zooKeeperContainerPort",
    "KAFKA_LOG4J_LOGGERS"                    -> "kafka.controller=INFO,kafka.producer.async.DefaultEventHandler=INFO,state.change.logger=INFO",
    "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR" -> "1"
  )

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val containerPort = ExposedPort.tcp(DefaultContainerPort)
    val portBinding   = new Ports()
    portBinding.bind(containerPort, Ports.Binding.bindPort(kafkaHostPort))
    val defaultHostConfig = newHostConfig().withPortBindings(portBinding)
    val hostConfig        = network.fold(defaultHostConfig) { n => defaultHostConfig.withNetworkMode(n.id) }
    val result = super
      .newCreateContainerCmd()
      .withEnv(env.map { case (k, v) => s"$k=$v" }.toArray: _*)
      .withExposedPorts(containerPort)
      .withHostConfig(hostConfig)
    network.fold(result) { n => result.withAliases(n.alias) }
  }

}
