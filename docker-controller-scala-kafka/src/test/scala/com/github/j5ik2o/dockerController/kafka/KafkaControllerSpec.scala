package com.github.j5ik2o.dockerController.kafka

import com.github.j5ik2o.dockerController.WaitPredicates.WaitPredicate
import com.github.j5ik2o.dockerController._
import org.scalatest.freespec.AnyFreeSpec

import scala.concurrent.duration.Duration

class KafkaControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport {
  val zkAlias    = Network("kafka", "zk1")
  val kafkaAlias = Network("kafka", "kafka1")

  val zooKeeperHostName: String = "zk1"
  val zooKeeperHostPort: Int    = RandomPortUtil.temporaryServerPort()

  val zooKeeperController: ZooKeeperController = ZooKeeperController(dockerClient)(
    1,
    dockerHost,
    zooKeeperHostPort,
    ZooKeeperController.DefaultZooPort,
    Some(zkAlias)
  )

  val kafkaHostName: String     = "kafka1"
  val kafkaHostPort: Int        = RandomPortUtil.temporaryServerPort()
  val createTopics: Seq[String] = Seq.empty

  val kafkaController =
    new KafkaController(dockerClient)(
      kafkaHostName,
      kafkaHostPort,
      zooKeeperHostName,
      zooKeeperController.containerPort,
      Some(kafkaAlias),
      createTopics
    )

  override protected val dockerControllers: Vector[DockerController] = Vector(zooKeeperController, kafkaController)

  val zooKeeperWaitPredicate: WaitPredicate =
    WaitPredicates.forLogMessageByRegex(ZooKeeperController.RegexForWaitPredicate)
  val zooKeeperWaitPredicateSetting: WaitPredicateSetting = WaitPredicateSetting(Duration.Inf, zooKeeperWaitPredicate)

  val kafkaWaitPredicate: WaitPredicate               = WaitPredicates.forLogMessageByRegex(KafkaController.RegexForWaitPredicate)
  val kafkaWaitPredicateSetting: WaitPredicateSetting = WaitPredicateSetting(Duration.Inf, kafkaWaitPredicate)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] = {
    Map(
      kafkaController     -> kafkaWaitPredicateSetting,
      zooKeeperController -> zooKeeperWaitPredicateSetting
    )
  }

  "KafkaController" - {
    "" in {}
  }

  override protected def beforeCreateContainers(): Unit = {
    dockerClient.createNetworkCmd().withName("kafka").exec()
  }

  override protected def afterRemoveContainers(): Unit = {
    dockerClient.removeNetworkCmd("kafka").exec()
  }

}
