package com.github.j5ik2o.dockerController.localstack

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.DockerControllerImpl
import com.github.j5ik2o.dockerController.localstack.LocalStackController._

import scala.concurrent.duration.{ DurationInt, FiniteDuration }

object LocalStackController {
  final val DefaultImageName              = "localstack/localstack"
  final val DefaultImageTag: Some[String] = Some("4.7")

  def apply(
      dockerClient: DockerClient,
      isDockerClientAutoClose: Boolean = false,
      outputFrameInterval: FiniteDuration = 500.millis,
      imageName: String = DefaultImageName,
      imageTag: Option[String] = DefaultImageTag,
      envVars: Map[String, String] = Map.empty
  )(
      services: Set[Service],
      edgeHostPort: Int,
      hostPorts: Map[Service, Int] = Map.empty,
      hostName: Option[String] = None,
      hostNameExternal: Option[String] = None,
      defaultRegion: Option[String] = None
  ): LocalStackController =
    new LocalStackController(dockerClient, isDockerClientAutoClose, outputFrameInterval, imageName, imageTag, envVars)(
      services,
      edgeHostPort,
      hostPorts,
      hostName,
      hostNameExternal,
      defaultRegion
    )
}

sealed abstract class Service(val entryName: String)

object Service {

  case object ACM extends Service("acm")

  case object ApiGateway extends Service("apigateway")

  case object CloudFormation extends Service("cloudformation")

  case object CloudWatch extends Service("cloudwatch")

  case object CloudWatchLogs extends Service("logs")

  case object DynamoDB extends Service("dynamodb")

  case object DynamoDBStreams extends Service("dynamodbstreams")

  case object EC2 extends Service("ec2")

  case object Elasticsearch extends Service("es")

  case object EventBridge extends Service("eventbridge")

  case object Firehose extends Service("firehose")

  case object IAM extends Service("iam")

  case object Kinesis extends Service("kinesis")

  case object KMS extends Service("kms")

  case object Lambda extends Service("lambda")

  case object RedShift extends Service("redshift")

  case object Route53 extends Service("route53")

  case object S3 extends Service("s3")

  case object SecretManager extends Service("secretsmanager")

  case object SES extends Service("ses")

  case object SNS extends Service("sns")

  case object SQS extends Service("sqs")

  case object SSM extends Service("ssm")

  case object StepFunctions extends Service("stepfunctions")

  case object STS extends Service("sts")

}

class LocalStackController(
    dockerClient: DockerClient,
    isDockerClientAutoClose: Boolean = false,
    outputFrameInterval: FiniteDuration = 500.millis,
    imageName: String = DefaultImageName,
    imageTag: Option[String] = DefaultImageTag,
    envVars: Map[String, String] = Map.empty
)(
    services: Set[Service],
    edgeHostPort: Int,
    hostPorts: Map[Service, Int],
    edgeBindHost: Option[String] = None,
    hostName: Option[String] = None,
    hostNameExternal: Option[String] = None,
    defaultRegion: Option[String] = None
) extends DockerControllerImpl(dockerClient, isDockerClientAutoClose, outputFrameInterval)(imageName, imageTag) {

  private val environmentVariables: Map[String, String] = Map(
    "EAGER_SERVICE_LOADING" -> "1",
    "SERVICES"              -> services.map(_.entryName).mkString(",")
  ) ++
    edgeBindHost.fold(Map.empty[String, String]) { e => Map("EDGE_BIND_HOST" -> e) } ++
    hostName.fold(Map.empty[String, String]) { h => Map("HOSTNAME" -> h) } ++
    hostNameExternal.fold(Map.empty[String, String]) { h => Map("HOSTNAME_EXTERNAL" -> h) } ++
    defaultRegion.fold(Map.empty[String, String]) { r => Map("DEFAULT_REGION" -> r) } ++
    hostPorts.foldLeft(Map.empty[String, String]) { case (result, (s, p)) =>
      result ++ Map(s.entryName.toUpperCase + "_PORT_EXTERNAL" -> p.toString)
    } ++
    envVars

  logger.debug(s"environmentVariables= $environmentVariables")

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val portBinding = new Ports()
    portBinding.bind(ExposedPort.tcp(4566), Ports.Binding.bindPort(edgeHostPort))

    super
      .newCreateContainerCmd()
      .withEnv(environmentVariables.map { case (k, v) => s"$k=$v" }.toArray: _*)
      .withHostConfig(newHostConfig().withPortBindings(portBinding))
  }

}
