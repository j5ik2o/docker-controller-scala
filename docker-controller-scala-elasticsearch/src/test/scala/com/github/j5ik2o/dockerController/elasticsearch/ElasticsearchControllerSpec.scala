package com.github.j5ik2o.dockerController.elasticsearch

import com.github.j5ik2o.dockerController.{
  DockerController,
  DockerControllerSpecSupport,
  RandomPortUtil,
  WaitPredicates
}
import org.elasticsearch.client.{ RequestOptions, RestClient, RestHighLevelClient }
import org.scalatest.freespec.AnyFreeSpec
import org.apache.http.HttpHost

import scala.concurrent.duration.{ Duration, DurationInt }

class ElasticsearchControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt

  val hostPort1: Int                      = RandomPortUtil.temporaryServerPort()
  val hostPort2: Int                      = RandomPortUtil.temporaryServerPort()
  val controller: ElasticsearchController = ElasticsearchController(dockerClient)(hostPort1, hostPort2)

  override protected val dockerControllers: Vector[DockerController] = Vector(controller)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] =
    Map(
      controller -> WaitPredicateSetting(
        Duration.Inf,
        WaitPredicates.forListeningHostTcpPort(
          dockerHost,
          hostPort1,
          (1 * testTimeFactor).seconds,
          Some((1 * testTimeFactor).seconds)
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
      } finally if (client != null) client.close()
    }
  }
}
