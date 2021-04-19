package com.github.j5ik2o.dockerController.dynamodbLocal

import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicAWSCredentials }
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.dynamodbv2.{ AmazonDynamoDB, AmazonDynamoDBClientBuilder }
import com.github.j5ik2o.dockerController.WaitPredicates.WaitPredicate
import com.github.j5ik2o.dockerController.{
  DockerController,
  DockerControllerSpecSupport,
  RandomPortUtil,
  WaitPredicates
}
import org.scalatest.freespec.AnyFreeSpec

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class DynamoDBLocalControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt
  logger.debug(s"testTimeFactor = $testTimeFactor")

  val hostPort: Int                       = RandomPortUtil.temporaryServerPort()
  val controller: DynamoDBLocalController = new DynamoDBLocalController(dockerClient)(hostPort)

  // val waitPredicate: WaitPredicate = WaitPredicates.forListeningHostTcpPort(dockerHost, hostPort)
  val waitPredicate: WaitPredicate = WaitPredicates.forLogMessageByRegex(
    DynamoDBLocalController.RegexOfWaitPredicate,
    Some((1 * testTimeFactor).seconds)
  )

  val waitPredicateSetting: WaitPredicateSetting = WaitPredicateSetting(Duration.Inf, waitPredicate)

  val tableName = "test"

  override protected val dockerControllers: Vector[DockerController] = Vector(controller)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] =
    Map(
      controller -> waitPredicateSetting
    )

  val dynamoDBEndpoint: String        = s"http://$dockerHost:$hostPort"
  val dynamoDBRegion: Regions         = Regions.AP_NORTHEAST_1
  val dynamoDBAccessKeyId: String     = "x"
  val dynamoDBSecretAccessKey: String = "x"

  protected val dynamoDBClient: AmazonDynamoDB = {
    AmazonDynamoDBClientBuilder
      .standard()
      .withEndpointConfiguration(new EndpointConfiguration(dynamoDBEndpoint, dynamoDBRegion.getName))
      .withCredentials(
        new AWSStaticCredentialsProvider(new BasicAWSCredentials(dynamoDBAccessKeyId, dynamoDBSecretAccessKey))
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

  "DynamoDBLocalController" - {
    "run-1" in {
      createTable()
    }
  }
}
