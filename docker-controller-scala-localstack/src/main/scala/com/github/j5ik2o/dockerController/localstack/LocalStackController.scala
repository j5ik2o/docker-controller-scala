package com.github.j5ik2o.dockerController.localstack

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.HostConfig.newHostConfig
import com.github.dockerjava.api.model.{ ExposedPort, Ports }
import com.github.j5ik2o.dockerController.DockerControllerImpl
import com.github.j5ik2o.dockerController.localstack.LocalStackController._
import enumeratum._

import scala.collection.immutable
import scala.concurrent.duration.{ DurationInt, FiniteDuration }

object LocalStackController {
  final val DefaultImageName = "localstack/localstack"
  final val DefaultImageTag  = Some("0.11.2")

  def apply(
      dockerClient: DockerClient,
      outputFrameInterval: FiniteDuration = 500.millis,
      imageName: String = DefaultImageName,
      imageTag: Option[String] = DefaultImageTag,
      envVars: Map[String, String] = Map.empty
  )(
      services: Set[Service],
      hostPorts: Map[Service, Int],
      hostName: Option[String] = None,
      hostNameExternal: Option[String] = None,
      defaultRegion: Option[String] = None
  ): LocalStackController =
    new LocalStackController(dockerClient, outputFrameInterval, imageName, imageTag, envVars)(
      services,
      hostPorts,
      hostName,
      hostNameExternal,
      defaultRegion
    )
}

sealed abstract class Service(override val entryName: String, val port: Int) extends EnumEntry

object Service extends Enum[Service] {
  override def values: immutable.IndexedSeq[Service] = findValues
  case object ApiGateway      extends Service("apigateway", 4567)
  case object DynamoDB        extends Service("dynamodb", 4569)
  case object DynamoDBStreams extends Service("dynamodbstreams", 4570)
  case object Elasticsearch   extends Service("es", 4571)
  case object S3              extends Service("s3", 4572)
  case object Firehose        extends Service("firehose", 4573)
  case object Lambda          extends Service("lambda", 4574)
  case object SNS             extends Service("sns", 4575)
  case object SQS             extends Service("sqs", 4576)
  case object RedShift        extends Service("redshift", 4577)
  case object SES             extends Service("ses", 4579)
  case object Route53         extends Service("route53", 4580)
  case object CloudFormation  extends Service("cloudformation", 4581)
  case object CloudWatch      extends Service("cloudwatch", 4582)
  case object SSM             extends Service("ssm", 4583)
  case object SecretManager   extends Service("secretsmanager", 4584)
  case object StepFunctions   extends Service("stepfunctions", 4585)
  case object CloudWatchLogs  extends Service("logs", 4586)
  case object STS             extends Service("sts", 4592)
  case object IAM             extends Service("iam", 4593)
  case object KMS             extends Service("kms", 4599)
}

class LocalStackController(
    dockerClient: DockerClient,
    outputFrameInterval: FiniteDuration = 500.millis,
    imageName: String = DefaultImageName,
    imageTag: Option[String] = DefaultImageTag,
    envVars: Map[String, String] = Map.empty
)(
    services: Set[Service],
    hostPorts: Map[Service, Int],
    hostName: Option[String] = None,
    hostNameExternal: Option[String] = None,
    defaultRegion: Option[String] = None
) extends DockerControllerImpl(dockerClient, outputFrameInterval)(imageName, imageTag) {

  private val environmentVariables: Map[String, String] = Map(
      "SERVICES" -> services.map(_.entryName).mkString(",")
    ) ++
    hostName.fold(Map.empty[String, String]) { h => Map("HOSTNAME"                  -> h) } ++
    hostNameExternal.fold(Map.empty[String, String]) { h => Map("HOSTNAME_EXTERNAL" -> h) } ++
    defaultRegion.fold(Map.empty[String, String]) { r => Map("DEFAULT_REGION"       -> r) } ++
    hostPorts.foldLeft(Map.empty[String, String]) {
      case (result, (s, p)) => result ++ Map(s.entryName.toUpperCase + "_PORT_EXTERNAL" -> p.toString)
    } ++ envVars

  logger.debug(s"environmentVariables= $environmentVariables")

  override protected def newCreateContainerCmd(): CreateContainerCmd = {
    val portBinding = new Ports()
    val containerPortWithHostPorts = hostPorts.map {
      case (service, port) =>
        (ExposedPort.tcp(service.port), port)
    }
    containerPortWithHostPorts.foreach {
      case (servicePort, hostPort) =>
        portBinding.bind(servicePort, Ports.Binding.bindPort(hostPort))
    }
    super
      .newCreateContainerCmd()
      .withEnv(environmentVariables.map { case (k, v) => s"$k=$v" }.toArray: _*)
      .withExposedPorts(containerPortWithHostPorts.keys.toArray: _*)
      .withHostConfig(newHostConfig().withPortBindings(portBinding))
  }
}
