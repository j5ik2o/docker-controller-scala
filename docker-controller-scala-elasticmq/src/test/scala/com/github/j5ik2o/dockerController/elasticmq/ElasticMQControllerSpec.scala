package com.github.j5ik2o.dockerController.elasticmq

import com.amazonaws.auth.{ AWSCredentialsProviderChain, AWSStaticCredentialsProvider, BasicAWSCredentials }
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.{ CreateQueueRequest, SendMessageRequest, SetQueueAttributesRequest }
import com.github.j5ik2o.dockerController.{
  DockerController,
  DockerControllerSpecSupport,
  RandomPortUtil,
  WaitPredicates
}
import org.scalatest.freespec.AnyFreeSpec

import java.util.UUID
import scala.concurrent.duration._

class ElasticMQControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport {

  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt
  logger.debug(s"testTimeFactor = $testTimeFactor")

  val hostPorts: Seq[Int]             = Seq(temporaryServerPort(), RandomPortUtil.temporaryServerPort())
  val controller: ElasticMQController = ElasticMQController(dockerClient)(dockerHost, hostPorts)

  override protected val dockerControllers: Vector[DockerController] = Vector(controller)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] =
    Map(
      controller -> WaitPredicateSetting(
        Duration.Inf,
        WaitPredicates.forListeningHostTcpPort(
          dockerHost,
          hostPorts.head,
          (1 * testTimeFactor).seconds,
          Some((5 * testTimeFactor).seconds)
        )
      )
    )

  "ElasticMQController" - {
    "run" ignore {
      val client = AmazonSQSClientBuilder
        .standard()
        .withCredentials(
          new AWSCredentialsProviderChain(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x")))
        )
        .withEndpointConfiguration(
          new AwsClientBuilder.EndpointConfiguration(
            s"http://${dockerHost}:${hostPorts.head}",
            Regions.DEFAULT_REGION.getName
          )
        ).build()

      val queueName = "test"
      val request = new CreateQueueRequest(queueName)
        .addAttributesEntry("VisibilityTimeout", "5")
        .addAttributesEntry("DelaySeconds", "1")

      val createQueueResult = client.createQueue(request)
      assert(createQueueResult.getSdkHttpMetadata.getHttpStatusCode == 200)
      val queueUrlResult = client.getQueueUrl(queueName)
      assert(queueUrlResult.getSdkHttpMetadata.getHttpStatusCode == 200)
      val queueUrl = queueUrlResult.getQueueUrl

      val setAttrsRequest = new SetQueueAttributesRequest()
        .withQueueUrl(queueUrl)
        .addAttributesEntry("ReceiveMessageWaitTimeSeconds", "5")
      val queueAttributesResult = client.setQueueAttributes(setAttrsRequest)
      assert(queueAttributesResult.getSdkHttpMetadata.getHttpStatusCode == 200)

      val text               = UUID.randomUUID().toString
      val sendMessageRequest = new SendMessageRequest(queueUrl, text)
      val sendMessageResult  = client.sendMessage(sendMessageRequest)
      assert(sendMessageResult.getSdkHttpMetadata.getHttpStatusCode == 200)

      val receiveMessageResult = client.receiveMessage(queueUrl)
      assert(receiveMessageResult.getSdkHttpMetadata.getHttpStatusCode == 200)
      assert(receiveMessageResult.getMessages.size() > 0)
      val message = receiveMessageResult.getMessages.get(0)
      assert(message.getBody == text)
    }
  }
}
