package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command._
import com.github.dockerjava.api.model.{
  ExposedPort,
  Frame,
  HostConfig,
  Image,
  NetworkSettings,
  Ports,
  PullResponseItem
}
import org.slf4j.{ Logger, LoggerFactory }

import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }
import java.util.{ Timer, TimerTask }
import scala.annotation.tailrec
import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration }
import scala.jdk.CollectionConverters._

trait DockerController {
  def containerId: String
  def createContainer(f: CreateContainerCmd => CreateContainerCmd = identity): DockerController
  def removeContainer(f: RemoveContainerCmd => RemoveContainerCmd = identity): DockerController
  def startContainer(f: StartContainerCmd => StartContainerCmd = identity): DockerController
  def stopContainer(f: StopContainerCmd => StopContainerCmd = identity): DockerController
  def inspectContainer(f: InspectContainerCmd => InspectContainerCmd = identity): InspectContainerResponse
  def listImages(f: ListImagesCmd => ListImagesCmd = identity): Vector[Image]
  def existsImage(p: Image => Boolean): Boolean
  def pullImageIfNotExists(f: PullImageCmd => PullImageCmd = identity): DockerController
  def pullImage(f: PullImageCmd => PullImageCmd = identity): DockerController
  def awaitCondition(duration: Duration)(predicate: Frame => Boolean): DockerController
}

class DockerControllerImpl(val dockerClient: DockerClient, outputFrameInterval: FiniteDuration = 500.millis)(
    val imageName: String,
    val tag: Option[String] = None
) extends DockerController {

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  private var _containerId: String = _

  private def repoTag: String = tag.fold(imageName)(t => s"$imageName:$t")

  override def containerId: String = _containerId

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

  override def createContainer(f: CreateContainerCmd => CreateContainerCmd): DockerController = {
    logger.debug("createContainer --- start")
    _containerId = f(newCreateContainerCmd()).exec().getId
    logger.debug("createContainer --- finish")
    this
  }

  override def removeContainer(f: RemoveContainerCmd => RemoveContainerCmd): DockerController = {
    logger.debug("removeContainer --- start")
    f(newRemoveContainerCmd()).exec()
    logger.debug("removeContainer --- finish")
    this
  }

  override def inspectContainer(f: InspectContainerCmd => InspectContainerCmd): InspectContainerResponse = {
    logger.debug("inspectContainer --- start")
    val result = f(newInspectContainerCmd()).exec()
    logger.debug("inspectContainer --- finish")
    result
  }

  override def listImages(f: ListImagesCmd => ListImagesCmd): Vector[Image] = {
    logger.debug("listImages --- start")
    val result = f(newListImagesCmd()).exec().asScala.toVector
    logger.debug("listImages --- finish")
    result
  }

  override def existsImage(p: Image => Boolean): Boolean = {
    logger.debug("exists --- start")
    val result = listImages().exists(p)
    logger.debug("exists --- finish")
    result
  }

  override def pullImageIfNotExists(f: PullImageCmd => PullImageCmd): DockerController = {
    logger.debug("pullImageIfNotExists --- start")
    if (!existsImage(p => p.getRepoTags.contains(repoTag))) {
      pullImage(f)
    }
    logger.debug("pullImageIfNotExists --- finish")
    this
  }

  override def pullImage(f: PullImageCmd => PullImageCmd): DockerController = {
    logger.debug("pullContainer --- start")
    f(newPullImageCmd())
      .exec(new ResultCallback.Adapter[PullResponseItem] {
        override def onNext(frame: PullResponseItem): Unit = {
          logger.debug(frame.toString)
        }
      })
      .awaitCompletion()
    logger.debug("pullContainer --- finish")
    this
  }

  override def startContainer(f: StartContainerCmd => StartContainerCmd): DockerController = {
    logger.debug("startContainer --- start")
    f(newStartContainerCmd()).exec()
    logger.debug("startContainer --- finish")
    this
  }

  override def stopContainer(f: StopContainerCmd => StopContainerCmd): DockerController = {
    logger.debug("stopContainer --- start")
    f(newStopContainerCmd()).exec()
    logger.debug("stopContainer --- finish")
    this
  }

  override def awaitCondition(duration: Duration)(predicate: Frame => Boolean): DockerController = {
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
