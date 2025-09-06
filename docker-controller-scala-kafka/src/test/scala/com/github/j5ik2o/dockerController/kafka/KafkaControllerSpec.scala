package com.github.j5ik2o.dockerController.kafka

import com.github.j5ik2o.dockerController.WaitPredicates.WaitPredicate
import com.github.j5ik2o.dockerController._
import org.apache.kafka.clients.consumer.{ ConsumerConfig, KafkaConsumer }
import org.apache.kafka.clients.producer.{ KafkaProducer, ProducerConfig, ProducerRecord }
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{ StringDeserializer, StringSerializer }
import org.scalatest.freespec.AnyFreeSpec

import java.time.{ Duration => JavaDuration, LocalDateTime }
import java.util.{ Collections, Properties }
import scala.concurrent.duration._
import scala.util.control.Breaks

class KafkaControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt
  logger.debug(s"testTimeFactor = $testTimeFactor")

  val topicName = "mytopic"

  val kafkaExternalHostPort: Int = temporaryServerPort()

  val kafkaController = new KafkaController(dockerClient)(
    kafkaExternalHostName = dockerHost,
    kafkaExternalHostPort = kafkaExternalHostPort,
    createTopics = Seq(topicName)
  )

  override protected val dockerControllers: Vector[DockerController] = Vector(kafkaController)

  val kafkaWaitPredicate: WaitPredicate =
    WaitPredicates.forLogMessageByRegex(KafkaController.RegexForWaitPredicate, Some((1 * testTimeFactor).seconds))
  val kafkaWaitPredicateSetting: WaitPredicateSetting = WaitPredicateSetting(Duration.Inf, kafkaWaitPredicate)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] = {
    Map(
      kafkaController -> kafkaWaitPredicateSetting
    )
  }

  "KafkaController" - {
    "produce&consume" in {
      val consumerRunnable = new Runnable {
        override def run(): Unit = {
          val consumerProperties = new Properties()
          consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, s"$dockerHost:$kafkaExternalHostPort")
          consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

          consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "myConsumerGroup")
          consumerProperties.put(
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            classOf[StringDeserializer].getName
          )
          consumerProperties.put(
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            classOf[StringDeserializer].getName
          )
          val consumer = new KafkaConsumer[String, String](consumerProperties)
          consumer.subscribe(Collections.singletonList(topicName))
          val b = new Breaks
          b.breakable {
            while (true) {
              try {
                logger.debug("consumer:=============================")
                val records = consumer.poll(JavaDuration.ofMillis(1000))
                logger.debug("consumer:=============================")
                logger.debug("[record size] " + records.count());
                records.forEach { record =>
                  logger.debug("consumer:=============================")
                  logger.debug("consumer:" + LocalDateTime.now)
                  logger.debug("consumer:topic: " + record.topic)
                  logger.debug("consumer:partition: " + record.partition)
                  logger.debug("consumer:key: " + record.key)
                  logger.debug("consumer:value: " + record.value)
                  logger.debug("consumer:offset: " + record.offset)
                  val topicPartition       = new TopicPartition(record.topic, record.partition)
                  val offsetAndMetadataMap = consumer.committed(java.util.Collections.singleton(topicPartition))
                  val offsetAndMetadata    = offsetAndMetadataMap.get(topicPartition)
                  if (offsetAndMetadata != null)
                    logger.debug("partition offset: " + offsetAndMetadata.offset)
                }
              } catch {
                case ex: org.apache.kafka.common.errors.InterruptException =>
                  logger.warn("occurred error", ex)
                  b.break()
                case ex: InterruptedException =>
                  logger.warn("occurred error", ex)
                  b.break()
              }
            }
          }
          try {
            consumer.close()
          } catch {
            case ex: org.apache.kafka.common.errors.InterruptException =>
              logger.warn("occurred error", ex)
            case ex: InterruptedException =>
              logger.warn("occurred error", ex)
          }
        }
      }
      val t = new Thread(consumerRunnable)
      t.start()
      val producerProperties = new Properties()
      producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, s"$dockerHost:$kafkaExternalHostPort")
      producerProperties.put(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        classOf[StringSerializer].getName
      )
      producerProperties.put(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        classOf[StringSerializer].getName
      )
      val producer = new KafkaProducer[String, String](producerProperties)
      (1 to 10).foreach { n =>
        val record         = new ProducerRecord[String, String](topicName, "my-value-" + n)
        val send           = producer.send(record)
        val recordMetadata = send.get
        logger.debug("producer:=============================")
        logger.debug("producer:" + LocalDateTime.now)
        logger.debug("producer:topic: " + recordMetadata.topic)
        logger.debug("producer:partition: " + recordMetadata.partition)
        logger.debug("producer:offset: " + recordMetadata.offset)
      }
      producer.close()
      Thread.sleep(1000 * 10)
      t.interrupt()
      t.join()
    }

  }

}
