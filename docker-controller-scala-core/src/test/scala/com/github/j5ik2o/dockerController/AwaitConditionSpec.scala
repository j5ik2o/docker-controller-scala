package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.{
  CreateContainerCmd,
  CreateContainerResponse,
  LogContainerCmd,
  RemoveContainerCmd
}
import com.github.dockerjava.api.model.{ Frame, StreamType }
import org.scalatest.freespec.AnyFreeSpec

import java.lang.reflect.{ InvocationHandler, Method, Proxy }
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, Paths }
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class AwaitConditionSpec extends AnyFreeSpec {
  Seq("deadline", "ready", "polling", "zero").foreach { scenario =>
    s"awaitCondition preserves $scenario behavior and releases its threads" in {
      val output = Files.createTempFile("await-condition-", ".log")
      val process = new ProcessBuilder(
        Paths.get(System.getProperty("java.home"), "bin", "java").toString,
        "-cp",
        System.getProperty("java.class.path"),
        "com.github.j5ik2o.dockerController.AwaitConditionProbe",
        scenario
      ).redirectErrorStream(true).redirectOutput(output.toFile).start()
      try {
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        val log      = new String(Files.readAllBytes(output), UTF_8)
        info(log)
        assert(finished, s"$scenario JVM did not finish within watchdog deadline:\n$log")
        assert(process.exitValue() == 0, log)
        assert(log.contains(s"passed: $scenario"), log)
      } finally {
        process.destroyForcibly()
        process.waitFor(5, TimeUnit.SECONDS)
        Files.deleteIfExists(output)
      }
    }
  }
}

object AwaitConditionProbe {
  private def proxy[A](kind: Class[A])(dispatch: (Method, Array[AnyRef]) => AnyRef): A =
    kind.cast(
      Proxy.newProxyInstance(
        kind.getClassLoader,
        Array[Class[_]](kind),
        new InvocationHandler {
          override def invoke(instance: Any, method: Method, args: Array[AnyRef]): AnyRef =
            dispatch(method, args)
        }
      )
    )

  def main(args: Array[String]): Unit = {
    println(s"probe java.version=${System.getProperty("java.version")}, scenario=${args(0)}")
    val scenario     = args(0)
    val timersBefore = Thread.getAllStackTraces.keySet().asScala.filter(_.getName.startsWith("Timer-")).toSet
    lazy val log: LogContainerCmd = proxy(classOf[LogContainerCmd]) { (method, arguments) =>
      method.getName match {
        case "exec" =>
          val callback = arguments(0).asInstanceOf[ResultCallback[Frame]]
          if (scenario == "ready") callback.onNext(new Frame(StreamType.STDOUT, "ready".getBytes(UTF_8)))
          callback
        case name if name.startsWith("with") => log
        case name                            => throw new UnsupportedOperationException(name)
      }
    }
    val response = new CreateContainerResponse()
    response.setId("fake-container")
    lazy val create: CreateContainerCmd = proxy(classOf[CreateContainerCmd]) { (method, _) =>
      method.getName match {
        case "exec"                          => response
        case name if name.startsWith("with") => create
        case name                            => throw new UnsupportedOperationException(name)
      }
    }
    val remove = proxy(classOf[RemoveContainerCmd]) { (_, _) => null }
    val client = proxy(classOf[DockerClient]) { (method, _) =>
      method.getName match {
        case "createContainerCmd" => create
        case "logContainerCmd"    => log
        case "removeContainerCmd" => remove
        case name                 => throw new UnsupportedOperationException(name)
      }
    }
    val controller = DockerController(client, outputFrameInterval = 10.millis)("fake")
    controller.createContainer()
    @volatile var worker: Thread = null
    @volatile var polls          = 0
    @volatile var ready          = false
    val duration = scenario match {
      case "ready"   => 1.hour
      case "polling" => Duration.Inf
      case "zero"    => Duration.Zero
      case _         => 250.millis
    }
    val start = System.nanoTime()
    controller.awaitCondition(duration) { frame =>
      worker = Thread.currentThread()
      if (frame.isEmpty) polls += 1
      ready = frame.exists(f => new String(f.getPayload, UTF_8) == "ready")
      ready || (scenario == "polling" && polls >= 3)
    }
    val elapsed = (System.nanoTime() - start).nanos
    assert(elapsed < 5.seconds, s"unexpected wait: $elapsed")
    if (scenario == "deadline") {
      assert(elapsed >= 200.millis, s"deadline returned too early: $elapsed")
      assert(polls > 0 && !ready, "deadline completion must not be confused with readiness")
    }
    if (scenario == "ready") assert(ready)
    if (scenario == "polling") assert(polls >= 3 && !ready)
    assert(worker == null || !worker.isAlive, "polling worker remains alive")
    val until = System.nanoTime() + 2.seconds.toNanos
    def newTimers: Set[Thread] =
      Thread.getAllStackTraces.keySet().asScala.filter(t => t.getName.startsWith("Timer-") && !timersBefore(t)).toSet
    while (newTimers.exists(_.isAlive) && System.nanoTime() < until) Thread.sleep(10)
    assert(!newTimers.exists(_.isAlive), "timeout Timer remains alive after awaitCondition returned")
    println(s"passed: $scenario, elapsed=$elapsed, polls=$polls, ready=$ready")
  }
}
