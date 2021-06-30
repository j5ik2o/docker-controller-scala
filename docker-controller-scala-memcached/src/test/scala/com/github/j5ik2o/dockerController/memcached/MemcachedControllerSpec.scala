package com.github.j5ik2o.dockerController.memcached

import com.github.j5ik2o.dockerController.{
  DockerController,
  DockerControllerSpecSupport,
  RandomPortUtil,
  WaitPredicates
}
import com.twitter.finagle.Memcached
import com.twitter.io.Buf
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec

import scala.concurrent.duration._

class MemcachedControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport with ScalaFutures {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt
  logger.debug(s"testTimeFactor = $testTimeFactor")

  val hostPort: Int                   = temporaryServerPort()
  val controller: MemcachedController = MemcachedController(dockerClient)(hostPort)

  override protected val dockerControllers: Vector[DockerController] = Vector(controller)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] = Map(
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

  "MemcachedController" - {
    "run" in {
      val client = Memcached.client.newRichClient(s"$dockerHost:$hostPort")
      val str    = "a"
      val buf    = Buf.Utf8(str)
      val resultFuture = for {
        _ <- client.set("1", buf)
        r <- client.get("1")
      } yield r
      val result = resultFuture.toCompletableFuture.get().get
      assert(result == buf)
    }
  }
}
