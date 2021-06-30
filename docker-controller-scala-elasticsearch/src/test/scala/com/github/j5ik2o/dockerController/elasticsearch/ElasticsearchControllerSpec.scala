package com.github.j5ik2o.dockerController.elasticsearch

import com.github.j5ik2o.dockerController.{
  DockerController,
  DockerControllerSpecSupport,
  WaitPredicates
}
import org.elasticsearch.client.{ RequestOptions, RestClient, RestHighLevelClient }
import org.scalatest.freespec.AnyFreeSpec
import org.apache.http.HttpHost

import scala.concurrent.duration.{ Duration, DurationInt }
import scala.util.control.NonFatal

class ElasticsearchControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt
  logger.debug(s"testTimeFactor = $testTimeFactor")

  val hostPort1: Int                      = temporaryServerPort()
  val hostPort2: Int                      = temporaryServerPort()
  val controller: ElasticsearchController = ElasticsearchController(dockerClient)(hostPort1, hostPort2)

  override protected val dockerControllers: Vector[DockerController] = Vector(controller)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] =
    Map(
      controller -> WaitPredicateSetting(
        Duration.Inf,
        WaitPredicates.forListeningHostTcpPort(
          dockerHost,
          hostPort1,
          (3 * testTimeFactor).seconds,
          Some((10 * testTimeFactor).seconds)
        )
      )
    )

  "ElasticsearchController" - {
    "run" in {
      var client: RestHighLevelClient = null
      try {
        client = new RestHighLevelClient(
          RestClient.builder(
            new HttpHost(dockerHost, hostPort1, "http"),
            new HttpHost(dockerHost, hostPort2, "http")
          )
        )
        val result = client.ping(RequestOptions.DEFAULT)
        assert(result)
      } catch {
        case NonFatal(ex) =>
          ex.printStackTrace()
          fail("occurred error", ex)
      } finally if (client != null) client.close()
    }
  }
}
