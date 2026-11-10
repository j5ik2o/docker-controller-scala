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
import java.util.concurrent.{ CountDownLatch, TimeUnit }
import java.util.concurrent.atomic.{ AtomicInteger, AtomicReference }
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class AwaitConditionSpec extends AnyFreeSpec {
  Seq(
    "deadline",
    "ready",
    "polling",
    "polling-finite",
    "zero",
    "interrupted",
    "failure-frame",
    "failure-none",
    "failure-inf",
    "callback-finite",
    "callback-inf",
    "callback-interrupted",
    "callback-error-ready",
    "callback-ready-error",
    "callback-two-errors",
    "callback-repeat",
    "callback-eof-finite",
    "callback-eof-inf",
    "callback-eof-poll-finite",
    "callback-eof-poll-inf",
    "callback-late-ready",
    "callback-late-deadline",
    "callback-eof-error"
  ).foreach { scenario =>
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
  private def callbackScenario(scenario: String): Unit = {
    val timersBefore = Thread.getAllStackTraces.keySet().asScala.filter(_.getName.startsWith("Timer-")).toSet
    val entered      = new CountDownLatch(1)
    val delivered    = new CountDownLatch(1)
    val done         = new CountDownLatch(1)
    val worker       = new AtomicReference[Thread]()
    val caught       = new AtomicReference[Throwable]()
    val background   = new AtomicReference[Throwable]()
    val closes       = new AtomicInteger()
    val polls        = new AtomicInteger()
    val cause        = new IllegalStateException("callback cause")
    val sentinel: Throwable = if (scenario == "callback-interrupted") {
      val error = new InterruptedException("callback interrupted")
      error.initCause(cause)
      error
    } else new IllegalArgumentException("callback failure", cause)
    val second         = new IllegalStateException("second callback failure")
    val eofOnly        = scenario == "callback-eof-finite" || scenario == "callback-eof-inf"
    val eofPolling     = scenario.startsWith("callback-eof-poll-")
    val late           = scenario.startsWith("callback-late-")
    val readyFirst     = scenario == "callback-ready-error" || scenario == "callback-late-ready"
    val expectsFailure = !eofOnly && !eofPolling && !late && !readyFirst
    val duration =
      if (scenario.endsWith("-inf")) Duration.Inf
      else if (eofOnly || scenario == "callback-late-deadline") 250.millis
      else 1.hour
    @volatile var ready  = false
    var delivery: Thread = null
    var repeated         = false
    lazy val log: LogContainerCmd = proxy(classOf[LogContainerCmd]) { (method, arguments) =>
      method.getName match {
        case "exec" =>
          val callback = arguments(0).asInstanceOf[ResultCallback[Frame]]
          if (!repeated) {
            delivery = new Thread(
              new Runnable {
                override def run(): Unit = {
                  try {
                    assert(entered.await(2, TimeUnit.SECONDS), "worker did not start after exec returned")
                    callback.onStart(new java.io.Closeable {
                      override def close(): Unit = { closes.incrementAndGet(); () }
                    })
                    def frame(): Unit = callback.onNext(new Frame(StreamType.STDOUT, "ready".getBytes(UTF_8)))
                    if (readyFirst) frame()
                    if (late) {
                      delivered.countDown()
                      assert(done.await(2, TimeUnit.SECONDS), "caller did not finish before late error")
                      callback.onError(sentinel)
                    } else if (eofOnly || eofPolling) callback.onComplete()
                    else {
                      if (scenario == "callback-eof-error") callback.onComplete()
                      callback.onError(sentinel)
                      if (scenario == "callback-error-ready") frame()
                      if (scenario == "callback-two-errors") callback.onError(second)
                    }
                  } catch {
                    case ex: Throwable => background.set(ex)
                  } finally delivered.countDown()
                }
              },
              "fake-callback-delivery"
            )
            delivery.start()
          }
          callback
        case name if name.startsWith("with") => log
        case name                            => throw new UnsupportedOperationException(name)
      }
    }
    val response = new CreateContainerResponse()
    response.setId("fake-callback")
    val create = proxy(classOf[CreateContainerCmd]) { (method, _) =>
      if (method.getName == "exec") response else throw new UnsupportedOperationException(method.getName)
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
    val controller = DockerController(client, outputFrameInterval = 10.millis)("fake-callback")
    controller.createContainer()
    val start = System.nanoTime()
    val caller = new Thread(
      new Runnable {
        override def run(): Unit = {
          try
            controller.awaitCondition(duration) { frame =>
              worker.set(Thread.currentThread())
              polls.incrementAndGet()
              if (entered.getCount > 0) {
                entered.countDown()
                assert(delivered.await(2, TimeUnit.SECONDS), "transport did not deliver events")
              }
              ready = frame.isDefined
              ready || (eofPolling && polls.get() >= 3)
            }
          catch {
            case ex: Throwable => caught.set(ex)
          } finally done.countDown()
        }
      },
      "fake-callback-caller"
    )
    caller.start()
    try {
      assert(delivered.await(3, TimeUnit.SECONDS), "delivery did not finish")
      if (scenario == "callback-eof-inf") {
        assert(!done.await(250, TimeUnit.MILLISECONDS), "EOF unexpectedly completed Inf wait")
        println("observer interrupts EOF Inf worker; product has not returned")
        worker.get().interrupt()
      }
      assert(done.await(3, TimeUnit.SECONDS), "callback left caller pending")
      caller.join(1000)
      delivery.join(1000)
      println(s"callerSame=${caught.get() eq sentinel}, causeSame=${Option(caught.get())
          .exists(_.getCause eq cause)}, fakeCloses=${closes.get()}")
      assert(background.get() == null, s"delivery failed: ${background.get()}")
      if (expectsFailure) {
        assert(caught.get() eq sentinel, "caller did not receive callback sentinel")
        assert(caught.get().getCause eq cause, "caller did not receive callback cause")
      } else assert(caught.get() == null, s"unexpected caller failure: ${caught.get()}")
      assert(closes.get() == 1, "super callback did not close fake stream exactly once")
      assert(ready == readyFirst)
      if (eofPolling) assert(polls.get() >= 3)
      if (scenario == "callback-eof-finite" || scenario == "callback-late-deadline")
        assert((System.nanoTime() - start).nanos >= 200.millis)
      if (scenario == "callback-repeat") { repeated = true; controller.awaitCondition(1.hour)(_ => true) }
    } finally {
      delivered.countDown()
      done.countDown()
      if (worker.get() != null && worker.get().isAlive) worker.get().interrupt()
      caller.join(2000)
      if (delivery != null) { delivery.interrupt(); delivery.join(2000) }
      assert(!caller.isAlive && !Option(worker.get()).exists(_.isAlive), "caller or worker remains alive")
      assert(delivery == null || !delivery.isAlive, "delivery remains alive")
      val timers =
        Thread.getAllStackTraces.keySet().asScala.filter(t => t.getName.startsWith("Timer-") && !timersBefore(t))
      timers.foreach(_.join(1000))
      assert(!timers.exists(_.isAlive), "timeout Timer remains alive")
      println(s"cleanup callerAlive=${caller.isAlive}, workerAlive=${Option(worker.get())
          .exists(_.isAlive)}, timersAlive=${timers.count(_.isAlive)}")
    }
    println(s"passed: $scenario, elapsed=${(System.nanoTime() - start).nanos}")
  }

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
    println(s"probe java.version=${System.getProperty("java.version")}, java.home=${System
        .getProperty("java.home")}, scenario=${args(0)}")
    if (args(0).startsWith("callback-")) {
      callbackScenario(args(0))
      return
    }
    val scenario     = args(0)
    val timersBefore = Thread.getAllStackTraces.keySet().asScala.filter(_.getName.startsWith("Timer-")).toSet
    lazy val log: LogContainerCmd = proxy(classOf[LogContainerCmd]) { (method, arguments) =>
      method.getName match {
        case "exec" =>
          val callback = arguments(0).asInstanceOf[ResultCallback[Frame]]
          if (scenario == "ready" || scenario == "failure-frame")
            callback.onNext(new Frame(StreamType.STDOUT, "ready".getBytes(UTF_8)))
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
    assert(controller.createContainer().getId == "fake-container")
    @volatile var worker: Thread = null
    @volatile var polls          = 0
    @volatile var ready          = false
    val cause                    = new IllegalStateException("predicate cause")
    val sentinel                 = new IllegalArgumentException("predicate failure", cause)
    val fails                    = scenario.startsWith("failure-")
    val duration = scenario match {
      case "ready" | "polling-finite" | "failure-frame" | "failure-none" => 1.hour
      case "polling" | "failure-inf"                                     => Duration.Inf
      case "zero"                                                        => Duration.Zero
      case _                                                             => 250.millis
    }
    val start                     = System.nanoTime()
    var caught: Option[Throwable] = None
    try {
      controller.awaitCondition(duration) { frame =>
        worker = Thread.currentThread()
        if (frame.isEmpty) polls += 1
        ready = frame.exists(f => new String(f.getPayload, UTF_8) == "ready")
        if (fails) {
          assert(frame.isDefined == (scenario == "failure-frame"), "unexpected predicate input")
          throw sentinel
        }
        if (scenario == "interrupted") throw new InterruptedException("predicate stopped")
        ready || ((scenario == "polling" || scenario == "polling-finite") && polls >= 3)
      }
    } catch {
      case ex: Throwable => caught = Some(ex)
    }
    if (fails) {
      println(s"callerSame=${caught.exists(_ eq sentinel)}, causeSame=${caught.exists(_.getCause eq cause)}")
      assert(caught.exists(_ eq sentinel), "caller did not receive the original predicate failure")
      assert(caught.exists(_.getCause eq cause), "caller did not receive the original cause")
    } else assert(caught.isEmpty, s"unexpected caller failure: $caught")
    val elapsed = (System.nanoTime() - start).nanos
    assert(elapsed < 5.seconds, s"unexpected wait: $elapsed")
    if (scenario == "deadline") {
      assert(elapsed >= 200.millis, s"deadline returned too early: $elapsed")
      assert(polls > 0 && !ready, "deadline completion must not be confused with readiness")
    }
    if (scenario == "ready") assert(ready)
    if (scenario == "polling" || scenario == "polling-finite") assert(polls >= 3 && !ready)
    assert(worker == null || !worker.isAlive, "polling worker remains alive")
    val until = System.nanoTime() + 2.seconds.toNanos
    def newTimers: Set[Thread] =
      Thread.getAllStackTraces.keySet().asScala.filter(t => t.getName.startsWith("Timer-") && !timersBefore(t)).toSet
    while (newTimers.exists(_.isAlive) && System.nanoTime() < until) Thread.sleep(10)
    assert(!newTimers.exists(_.isAlive), "timeout Timer remains alive after awaitCondition returned")
    println(s"passed: $scenario, elapsed=$elapsed, polls=$polls, ready=$ready")
  }
}
