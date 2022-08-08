package com.github.j5ik2o.dockerController.localstack

import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicAWSCredentials }
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.model.{
  AttributeDefinition,
  CreateTableRequest,
  GlobalSecondaryIndex,
  KeySchemaElement,
  KeyType,
  Projection,
  ProjectionType,
  ProvisionedThroughput,
  ScalarAttributeType,
  StreamSpecification,
  StreamViewType
}
import com.amazonaws.services.dynamodbv2.{ AmazonDynamoDB, AmazonDynamoDBClientBuilder }
import com.amazonaws.services.s3.model.CreateBucketRequest
import com.amazonaws.services.s3.{ AmazonS3, AmazonS3Client }
import com.github.j5ik2o.dockerController.{ DockerController, DockerControllerSpecSupport, WaitPredicates }
import org.scalatest.freespec.AnyFreeSpec

import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._

class LocalStackControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport {
  val accessKeyId: String         = "AKIAIOSFODNN7EXAMPLE"
  val secretAccessKey: String     = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  val hostPort: Int               = temporaryServerPort()
  val endpointForS3: String       = s"http://$dockerHost:$hostPort"
  val endpointForDynamoDB: String = s"http://$dockerHost:$hostPort"
  val region: Regions             = Regions.AP_NORTHEAST_1

  val controller: LocalStackController =
    LocalStackController(dockerClient)(
      services = Set(Service.S3, Service.DynamoDB),
      edgeHostPort = hostPort,
      hostNameExternal = Some(dockerHost),
      defaultRegion = Some(region.getName)
    )

  override protected val dockerControllers: Vector[DockerController] = Vector(controller)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] =
    Map(
      controller -> WaitPredicateSetting(Duration.Inf, WaitPredicates.forLogMessageExactly("Ready."))
    )

  val bucketName = "test"
  val tableName  = "test"

  protected val s3Client: AmazonS3 = {
    AmazonS3Client
      .builder()
      .withEndpointConfiguration(new EndpointConfiguration(endpointForS3, region.getName))
      .withCredentials(
        new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKeyId, secretAccessKey))
      )
      .withPathStyleAccessEnabled(true)
      .build()
  }

  protected def createBucket(): Unit = {
    if (!s3Client.listBuckets().asScala.exists(_.getName == bucketName)) {
      val request = new CreateBucketRequest(bucketName, region.getName)
      s3Client.createBucket(request)
      logger.info(s"bucket created: $bucketName")
    }
    while (!s3Client.listBuckets().asScala.exists(_.getName == bucketName)) {
      logger.info(s"Waiting for the bucket to be created: $bucketName")
      Thread.sleep(500)
    }
  }

  protected val dynamoDBClient: AmazonDynamoDB = {
    AmazonDynamoDBClientBuilder
      .standard()
      .withEndpointConfiguration(new EndpointConfiguration(endpointForDynamoDB, region.getName))
      .withCredentials(
        new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKeyId, secretAccessKey))
      )
      .build()
  }

  protected def createTable(): Unit = {
    val listTablesResult = dynamoDBClient.listTables(2)
    if (!listTablesResult.getTableNames.asScala.exists(_.contains(tableName))) {
      val createRequest = new CreateTableRequest()
        .withTableName(tableName)
        .withAttributeDefinitions(
          Seq(
            new AttributeDefinition().withAttributeName("pkey").withAttributeType(ScalarAttributeType.S),
            new AttributeDefinition().withAttributeName("skey").withAttributeType(ScalarAttributeType.S),
            new AttributeDefinition().withAttributeName("persistence-id").withAttributeType(ScalarAttributeType.S),
            new AttributeDefinition().withAttributeName("sequence-nr").withAttributeType(ScalarAttributeType.N),
            new AttributeDefinition().withAttributeName("tags").withAttributeType(ScalarAttributeType.S)
          ).asJava
        ).withKeySchema(
          Seq(
            new KeySchemaElement().withAttributeName("pkey").withKeyType(KeyType.HASH),
            new KeySchemaElement().withAttributeName("skey").withKeyType(KeyType.RANGE)
          ).asJava
        ).withProvisionedThroughput(
          new ProvisionedThroughput().withReadCapacityUnits(10L).withWriteCapacityUnits(10L)
        ).withGlobalSecondaryIndexes(
          Seq(
            new GlobalSecondaryIndex()
              .withIndexName("TagsIndex").withKeySchema(
                Seq(
                  new KeySchemaElement().withAttributeName("tags").withKeyType(KeyType.HASH)
                ).asJava
              ).withProjection(new Projection().withProjectionType(ProjectionType.ALL))
              .withProvisionedThroughput(
                new ProvisionedThroughput().withReadCapacityUnits(10L).withWriteCapacityUnits(10L)
              ),
            new GlobalSecondaryIndex()
              .withIndexName("GetJournalRowsIndex").withKeySchema(
                Seq(
                  new KeySchemaElement().withAttributeName("persistence-id").withKeyType(KeyType.HASH),
                  new KeySchemaElement().withAttributeName("sequence-nr").withKeyType(KeyType.RANGE)
                ).asJava
              ).withProjection(new Projection().withProjectionType(ProjectionType.ALL))
              .withProvisionedThroughput(
                new ProvisionedThroughput().withReadCapacityUnits(10L).withWriteCapacityUnits(10L)
              )
          ).asJava
        ).withStreamSpecification(
          new StreamSpecification().withStreamEnabled(true).withStreamViewType(StreamViewType.NEW_IMAGE)
        )
      val createResponse = dynamoDBClient.createTable(createRequest)
      require(createResponse.getSdkHttpMetadata.getHttpStatusCode == 200)
    }
  }

  "LocalStackController" - {
    "run" in {
      createBucket()
      createTable()
    }
  }
}
