package com.github.j5ik2o.dockerController.minio

import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicAWSCredentials }
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{ AmazonS3, AmazonS3Client }
import com.github.j5ik2o.dockerController.WaitPredicates.WaitPredicate
import com.github.j5ik2o.dockerController._
import org.scalatest.freespec.AnyFreeSpec

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class MinioControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt
  logger.debug(s"testTimeFactor = $testTimeFactor")

  val minioAccessKeyId: String     = "AKIAIOSFODNN7EXAMPLE"
  val minioSecretAccessKey: String = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  val minioHost: String            = DockerClientConfigUtil.dockerHost(dockerClientConfig)
  val minioPort: Int               = temporaryServerPort()
  val minioEndpoint: String        = s"http://$minioHost:$minioPort"
  val minioRegion: Regions         = Regions.AP_NORTHEAST_1

  val controller: MinioController = MinioController(dockerClient)(minioPort, minioAccessKeyId, minioSecretAccessKey)

  // val waitPredicate: WaitPredicate = WaitPredicates.forListeningHostTcpPort(dockerHost, minioPort)
  val waitPredicate: WaitPredicate =
    WaitPredicates.forLogMessageByRegex(MinioController.RegexForWaitPredicate, Some((1 * testTimeFactor).seconds))

  val waitPredicateSetting: WaitPredicateSetting = WaitPredicateSetting(Duration.Inf, waitPredicate)

  val bucketName = "test"

  override protected val dockerControllers: Vector[DockerController] = Vector(controller)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] =
    Map(
      controller -> waitPredicateSetting
    )

  protected val s3Client: AmazonS3 = {
    AmazonS3Client
      .builder()
      .withEndpointConfiguration(new EndpointConfiguration(minioEndpoint, minioRegion.getName))
      .withCredentials(
        new AWSStaticCredentialsProvider(new BasicAWSCredentials(minioAccessKeyId, minioSecretAccessKey))
      )
      .build()
  }

  protected def createBucket(): Unit = {
    if (!s3Client.listBuckets().asScala.exists(_.getName == bucketName)) {
      s3Client.createBucket(bucketName)
      logger.info(s"bucket created: $bucketName")
    }
    while (!s3Client.listBuckets().asScala.exists(_.getName == bucketName)) {
      logger.info(s"Waiting for the bucket to be created: $bucketName")
      Thread.sleep(500)
    }
  }

  "MinioController" - {
    "run" in {
      createBucket()
    }
  }
}
