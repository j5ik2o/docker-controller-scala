package com.github.j5ik2o.dockerController.redis

import com.github.j5ik2o.dockerController.{
  DockerController,
  DockerControllerSpecSupport,
  RandomPortUtil,
  WaitPredicates
}
import com.redis.RedisClient
import org.scalatest.freespec.AnyFreeSpec

import scala.concurrent.duration._

class RedisControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt
  logger.debug(s"testTimeFactor = $testTimeFactor")

  val hostPort: Int                                                  = RandomPortUtil.temporaryServerPort()
  val controller: RedisController                                    = RedisController(dockerClient)(hostPort)
  override protected val dockerControllers: Vector[DockerController] = Vector(controller)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] =
    Map(
      controller -> WaitPredicateSetting(
        Duration.Inf,
        WaitPredicates.forListeningHostTcpPort(
          dockerHost,
          hostPort,
          (1 * testTimeFactor).seconds,
          Some((5 * testTimeFactor).seconds)
        )
      )
    )

  "RedisController" - {
    "run" in {
      val r = new RedisClient(dockerHost, hostPort)
      r.set("1", "2")
      assert(r.get("1").get == "2")
      r.close()
    }
  }
}
