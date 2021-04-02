package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command._
import com.github.dockerjava.api.model.{ ExposedPort, Frame, Image, NetworkSettings, Ports, PullResponseItem }
import org.slf4j.{ Logger, LoggerFactory }

import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }
import java.util.{ Timer, TimerTask }
import scala.annotation.tailrec
import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration }
import scala.jdk.CollectionConverters._

class DockerController(val dockerClient: DockerClient, outputFrameInterval: FiniteDuration = 500.millis)(
    val imageName: String,
    val tag: Option[String] = None
) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private var _containerId: String = _

  private def repoTag: String = tag.fold(imageName)(t => s"$imageName:$t")

  def containerId: String = _containerId

  protected def newCreateContainerCmd(): CreateContainerCmd = {
    dockerClient
      .createContainerCmd(repoTag)
  }

  protected def newRemoveContainerCmd(): RemoveContainerCmd = {
    require(containerId != null)
    dockerClient.removeContainerCmd(containerId)
  }

  protected def newInspectContainerCmd(): InspectContainerCmd = {
    require(containerId != null)
    dockerClient.inspectContainerCmd(containerId)
  }

  protected def newListImagesCmd(): ListImagesCmd = {
    dockerClient.listImagesCmd()
  }

  protected def newPullImageCmd(): PullImageCmd = {
    require(imageName != null)
    val cmd = dockerClient.pullImageCmd(imageName)
    tag.fold(cmd)(t => cmd.withTag(t))
  }

  protected def newLogContainerCmd(): LogContainerCmd = {
    require(containerId != null)
    dockerClient
      .logContainerCmd(containerId)
      .withStdOut(true)
      .withStdErr(true)
      .withFollowStream(true)
      .withTailAll()
  }

  protected def newStartContainerCmd(): StartContainerCmd = {
    require(containerId != null)
    dockerClient.startContainerCmd(containerId)
  }

  protected def newStopContainerCmd(): StopContainerCmd = {
    require(containerId != null)
    dockerClient.stopContainerCmd(containerId)
  }

  def createContainer(): DockerController = {
    logger.debug("createContainer --- start")
    _containerId = newCreateContainerCmd().exec().getId
    logger.debug("createContainer --- finish")
    this
  }

  def removeContainer(): DockerController = {
    logger.debug("removeContainer --- start")
    newRemoveContainerCmd().exec()
    logger.debug("removeContainer --- finish")
    this
  }

  def inspectContainer(): InspectContainerResponse = {
    newInspectContainerCmd().exec()
  }

  def networkSettings(): NetworkSettings = {
    inspectContainer().getNetworkSettings
  }

  def ports: Ports = {
    networkSettings().getPorts
  }

  def portBindings: Map[ExposedPort, Array[Ports.Binding]] = {
    ports.getBindings.asScala.toMap
  }

  def portBinding(exposedPort: ExposedPort): Option[Array[Ports.Binding]] = {
    portBindings.get(exposedPort)
  }

  def bindingHostPorts(exposedPort: ExposedPort): Option[Array[Int]] = {
    portBinding(exposedPort).map(_.map(_.getHostPortSpec.toInt))
  }

  def bindingHostTcpPorts(exposedPort: Int): Option[Array[Int]] = {
    bindingHostPorts(ExposedPort.tcp(exposedPort))
  }

  def bindingHostUdpPorts(exposedPort: Int): Option[Array[Int]] = {
    bindingHostPorts(ExposedPort.udp(exposedPort))
  }

  def bindingHostPort(exposedPort: ExposedPort): Option[Int] = {
    portBinding(exposedPort).flatMap(_.headOption.map(_.getHostPortSpec.toInt))
  }

  def bindingHostTcpPort(exposedPort: Int): Option[Int] = {
    bindingHostPort(ExposedPort.tcp(exposedPort))
  }

  def bindingHostUdpPort(exposedPort: Int): Option[Int] = {
    bindingHostPort(ExposedPort.udp(exposedPort))
  }

  def listImages(): Vector[Image] = {
    logger.debug("listImages --- start")
    val result = newListImagesCmd().exec().asScala.toVector
    logger.debug("listImages --- finish")
    result
  }

  def existsImage(p: Image => Boolean): Boolean = {
    logger.debug("exists --- start")
    val result = listImages().exists(p)
    logger.debug("exists --- finish")
    result
  }

  def pullImageIfNotExists(): DockerController = {
    logger.debug("pullImageIfNotExists --- start")
    if (!existsImage(p => p.getRepoTags.contains(repoTag))) {
      pullImage()
    }
    logger.debug("pullImageIfNotExists --- finish")
    this
  }

  def pullImage(): DockerController = {
    logger.debug("pullContainer --- start")
    newPullImageCmd()
      .exec(new ResultCallback.Adapter[PullResponseItem] {
        override def onNext(frame: PullResponseItem): Unit = {
          logger.debug(frame.toString)
        }
      })
      .awaitCompletion()
    logger.debug("pullContainer --- finish")
    this
  }

  def startContainer(): DockerController = {
    logger.debug("startContainer --- start")
    newStartContainerCmd().exec()
    logger.debug("startContainer --- finish")
    this
  }

  def stopContainer(): DockerController = {
    logger.debug("stopContainer --- start")
    newStopContainerCmd().exec()
    logger.debug("stopContainer --- finish")
    this
  }

  def awaitCondition(duration: Duration)(predicate: Frame => Boolean): DockerController = {
    logger.debug("awaitCompletion --- start")
    val frameQueue: LinkedBlockingQueue[Frame] = new LinkedBlockingQueue[Frame]()

    newLogContainerCmd().exec(new ResultCallback.Adapter[Frame] {

      override def onNext(frame: Frame): Unit = {
        frameQueue.add(frame)
      }

    })

    @volatile var terminate = false
    val waiter = new Runnable {
      override def run(): Unit = {
        @tailrec
        def loop(): Unit = {
          if (!terminate && {
                val frame = frameQueue.poll(outputFrameInterval.toMillis, TimeUnit.MILLISECONDS)
                if (frame != null) {
                  logger.debug(frame.toString)
                  !predicate(frame)
                } else true
              }) {
            loop()
          }
        }
        try {
          loop()
        } catch {
          case _: InterruptedException =>
            logger.debug("interrupted")
        }
      }
    }

    val thread = new Thread(waiter)
    thread.start()
    if (duration.isFinite) {
      val timer = new Timer()
      timer.schedule(new TimerTask {
        override def run(): Unit = {
          terminate = true
          thread.interrupt()
        }
      }, duration.toMillis)
      timer.cancel()
    }
    thread.join()
    logger.debug("awaitCompletion --- finish")
    this
  }

}
