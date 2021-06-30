package com.github.j5ik2o.dockerController.redis

import com.github.j5ik2o.dockerController.{
  DockerController,
  DockerControllerSpecSupport,
  WaitPredicates
}
import com.redis.RedisClient
import org.scalatest.freespec.AnyFreeSpec

import scala.concurrent.duration._
import scala.util.control.NonFatal

class RedisControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt
  logger.debug(s"testTimeFactor = $testTimeFactor")

  val hostPort: Int               = temporaryServerPort()
  val controller: RedisController = RedisController(dockerClient)(hostPort)

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
      var redisClient: RedisClient = null
      try {
        redisClient = new RedisClient(dockerHost, hostPort)
        redisClient.set("1", "2")
        assert(redisClient.get("1").get == "2")
      } catch {
        case NonFatal(ex) =>
          fail(ex)
      } finally {
        if (redisClient != null)
          redisClient.close()
      }
    }
  }
}
