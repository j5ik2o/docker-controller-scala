package com.github.j5ik2o.dockerController.elasticsearch

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.github.j5ik2o.dockerController.{ DockerController, DockerControllerSpecSupport, WaitPredicates }
import org.elasticsearch.client.RestClient
import org.scalatest.freespec.AnyFreeSpec
import org.apache.http.HttpHost

import scala.concurrent.duration.{ Duration, DurationInt }
import scala.util.control.NonFatal

class ElasticsearchControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt
  logger.debug(s"testTimeFactor = $testTimeFactor")

  val hostPort1: Int = temporaryServerPort()
  val hostPort2: Int = temporaryServerPort()
  val controller: ElasticsearchController = ElasticsearchController(
    dockerClient,
    envVars = Map(
      "discovery.type"                  -> "single-node",
      "xpack.security.enabled"          -> "false",
      "xpack.security.http.ssl.enabled" -> "false",
      "ES_JAVA_OPTS"                    -> "-Xms512m -Xmx512m"
    )
  )(hostPort1, hostPort2)

  override protected val dockerControllers: Vector[DockerController] = Vector(controller)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] =
    Map(
      controller -> WaitPredicateSetting(
        Duration.Inf,
        WaitPredicates.forListeningHostTcpPort(
          dockerHost,
          hostPort1,
          (30 * testTimeFactor).seconds,
          Some((20 * testTimeFactor).seconds)
        )
      )
    )

  "ElasticsearchController" - {
    "run" in {
      var httpClient: RestClient         = null
      var transport: RestClientTransport = null
      try {
        httpClient = RestClient
          .builder(
            new HttpHost("localhost", hostPort1)
          ).build()
        transport = new RestClientTransport(
          httpClient,
          new JacksonJsonpMapper()
        )
        val esClient = new ElasticsearchClient(transport)
        // Use info() instead of ping() for compatibility
        val info = esClient.info()
        assert(info.clusterName() != null)
      } finally {
        if (transport != null)
          transport.close()
        if (httpClient != null)
          httpClient.close()
      }
    }
  }
}
