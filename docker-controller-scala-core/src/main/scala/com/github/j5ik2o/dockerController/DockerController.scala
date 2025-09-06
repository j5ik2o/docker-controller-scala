package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command._
import com.github.dockerjava.api.model.{ Frame, Image, PullResponseItem }
import me.tongfei.progressbar.{ DelegatingProgressBarConsumer, ProgressBar, ProgressBarBuilder, ProgressBarStyle }
import org.slf4j.{ Logger, LoggerFactory }

import java.lang
import java.util.concurrent.{ LinkedBlockingQueue, TimeUnit }
import java.util.{ Timer, TimerTask }
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration }
import scala.jdk.CollectionConverters._

case class CmdConfigures(
    createContainerCmdConfigure: CreateContainerCmd => CreateContainerCmd = identity,
    removeContainerCmdConfigure: RemoveContainerCmd => RemoveContainerCmd = identity,
    startContainerCmdConfigure: StartContainerCmd => StartContainerCmd = identity,
    stopContainerCmdConfigure: StopContainerCmd => StopContainerCmd = identity,
    inspectContainerCmdConfigure: InspectContainerCmd => InspectContainerCmd = identity,
    listImageCmdConfigure: ListImagesCmd => ListImagesCmd = identity,
    pullImageCmdConfigure: PullImageCmd => PullImageCmd = identity
)

trait DockerController {
  def containerId: Option[String]

  def dockerClient: DockerClient

  def isDockerClientAutoClose: Boolean

  def dispose(): Unit
  def imageName: String
  def tag: Option[String]

  def cmdConfigures: Option[CmdConfigures]
  def configureCmds(cmdConfigures: CmdConfigures): DockerController

  def createNetwork(name: String, f: CreateNetworkCmd => CreateNetworkCmd = identity): CreateNetworkResponse
  def removeNetwork(id: String, f: RemoveNetworkCmd => RemoveNetworkCmd = identity): Unit

  def configureCreateContainerCmd(f: CreateContainerCmd => CreateContainerCmd = identity): DockerController = {
    val newCmdConfigures = cmdConfigures match {
      case Some(cc) =>
        cc.copy(createContainerCmdConfigure = f)
      case None =>
        CmdConfigures(createContainerCmdConfigure = f)
    }
    configureCmds(newCmdConfigures)
  }

  def createContainer(f: CreateContainerCmd => CreateContainerCmd = identity): CreateContainerResponse
  def removeContainer(f: RemoveContainerCmd => RemoveContainerCmd = identity): Unit
  def startContainer(f: StartContainerCmd => StartContainerCmd = identity): Unit
  def stopContainer(f: StopContainerCmd => StopContainerCmd = identity): Unit
  def inspectContainer(f: InspectContainerCmd => InspectContainerCmd = identity): InspectContainerResponse
  def listImages(f: ListImagesCmd => ListImagesCmd = identity): Vector[Image]
  def existsImage(p: Image => Boolean): Boolean
  def pullImageIfNotExists(f: PullImageCmd => PullImageCmd = identity): Unit
  def pullImage(f: PullImageCmd => PullImageCmd = identity): Unit
  def awaitCondition(duration: Duration)(predicate: Option[Frame] => Boolean): Unit
}

object DockerController {

  def apply(
      dockerClient: DockerClient,
      isDockerClientAutoClose: Boolean = false,
      outputFrameInterval: FiniteDuration = 500.millis
  )(
      imageName: String,
      tag: Option[String] = None
  ): DockerController =
    new DockerControllerImpl(dockerClient, isDockerClientAutoClose, outputFrameInterval)(imageName, tag)
}

private[dockerController] class DockerControllerImpl(
    val dockerClient: DockerClient,
    val isDockerClientAutoClose: Boolean = false,
    outputFrameInterval: FiniteDuration = 500.millis
)(
    val imageName: String,
    _tag: Option[String] = None
) extends DockerController {

  val tag: Option[String] = _tag.orElse(Some("latest"))

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  private var _containerId: Option[String] = None

  private def repoTag: String = tag.fold(imageName)(t => s"$imageName:$t")

  override def containerId: Option[String] = _containerId

  private var _cmdConfigures: Option[CmdConfigures] = None

  private final val MaxProgressBarLength = 120

  private val progressBarConsumer =
    new DelegatingProgressBarConsumer({ text => logger.info(text) }, MaxProgressBarLength)

  override def cmdConfigures: Option[CmdConfigures] = _cmdConfigures

  override def configureCmds(cmdConfigures: CmdConfigures): DockerController = {
    this._cmdConfigures = Some(cmdConfigures)
    this
  }

  protected def isPlatformLinuxAmd64AtM1Mac: Boolean = false

  protected def newCreateContainerCmd(): CreateContainerCmd = {
    var cmd    = dockerClient.createContainerCmd(repoTag)
    val osArch = sys.props("os.arch")
    if (isPlatformLinuxAmd64AtM1Mac && osArch == "aarch64") {
      cmd = cmd.withPlatform("linux/amd64")
    }
    cmd
  }

  protected def newRemoveContainerCmd(): RemoveContainerCmd = {
    require(containerId.isDefined)
    dockerClient.removeContainerCmd(containerId.get)
  }

  protected def newInspectContainerCmd(): InspectContainerCmd = {
    require(containerId.isDefined)
    dockerClient.inspectContainerCmd(containerId.get)
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
    require(containerId.isDefined)
    dockerClient
      .logContainerCmd(containerId.get)
      .withStdOut(true)
      .withStdErr(true)
      .withFollowStream(true)
      .withTailAll()
  }

  protected def newStartContainerCmd(): StartContainerCmd = {
    require(containerId.isDefined)
    dockerClient.startContainerCmd(containerId.get)
  }

  protected def newStopContainerCmd(): StopContainerCmd = {
    require(containerId.isDefined)
    dockerClient.stopContainerCmd(containerId.get)
  }

  override def createContainer(f: CreateContainerCmd => CreateContainerCmd): CreateContainerResponse = synchronized {
    logger.debug("createContainer --- start")
    val configureFunction: CreateContainerCmd => CreateContainerCmd =
      cmdConfigures.map(_.createContainerCmdConfigure).getOrElse(identity)
    val result = f(configureFunction(newCreateContainerCmd())).exec()
    _containerId = Some(result.getId)
    sys.addShutdownHook {
      logger.debug("shutdownHook: start")
      dispose()
      logger.debug("shutdownHook: finish")
    }
    logger.debug("createContainer --- finish")
    result
  }

  override def removeContainer(f: RemoveContainerCmd => RemoveContainerCmd): Unit = synchronized {
    logger.debug("removeContainer --- start")
    val configureFunction: RemoveContainerCmd => RemoveContainerCmd =
      cmdConfigures.map(_.removeContainerCmdConfigure).getOrElse(identity)
    f(configureFunction(newRemoveContainerCmd())).exec()
    _containerId = None
    logger.debug("removeContainer --- finish")
  }

  override def inspectContainer(f: InspectContainerCmd => InspectContainerCmd): InspectContainerResponse = {
    logger.debug("inspectContainer --- start")
    val configureFunction: InspectContainerCmd => InspectContainerCmd =
      cmdConfigures.map(_.inspectContainerCmdConfigure).getOrElse(identity)
    val result = f(configureFunction(newInspectContainerCmd())).exec()
    logger.debug("inspectContainer --- finish")
    result
  }

  override def listImages(f: ListImagesCmd => ListImagesCmd): Vector[Image] = {
    logger.debug("listImages --- start")
    val configureFunction: ListImagesCmd => ListImagesCmd =
      cmdConfigures.map(_.listImageCmdConfigure).getOrElse(identity)
    val result = f(configureFunction(newListImagesCmd())).exec().asScala.toVector
    logger.debug("listImages --- finish")
    result
  }

  override def existsImage(p: Image => Boolean): Boolean = {
    logger.debug("exists --- start")
    val result = listImages().exists(p)
    logger.debug("exists --- finish")
    result
  }

  override def pullImageIfNotExists(f: PullImageCmd => PullImageCmd): Unit = {
    logger.debug("pullImageIfNotExists --- start")
    if (!existsImage(p => Option(p.getRepoTags).exists(_.contains(repoTag)))) {
      pullImage(f)
    }
    logger.debug("pullImageIfNotExists --- finish")
  }

  override def pullImage(f: PullImageCmd => PullImageCmd): Unit = {
    logger.debug("pullContainer --- start")
    val progressBarMap = mutable.Map.empty[String, ProgressBar]
    try {
      f(newPullImageCmd())
        .exec(new ResultCallback.Adapter[PullResponseItem] {
          override def onNext(frame: PullResponseItem): Unit = {
            if (frame.getProgressDetail != null) {
              val max     = frame.getProgressDetail.getTotal
              val current = frame.getProgressDetail.getCurrent
              val progressBar = progressBarMap.getOrElseUpdate(
                frame.getId,
                newProgressBar(frame, max)
              )
              progressBar.maxHint(max).stepTo(current)
            }
          }
        })
        .awaitCompletion()
    } finally {
      progressBarMap.foreach { case (_, progressBar) =>
        progressBar.close()
      }
    }
    logger.debug("pullContainer --- finish")
  }

  protected def newProgressBar(frame: PullResponseItem, max: lang.Long): ProgressBar = {
    new ProgressBarBuilder()
      .setTaskName(s"pull image: ${frame.getStatus}, ${frame.getId}")
      .setStyle(ProgressBarStyle.ASCII)
      .setConsumer(progressBarConsumer)
      .setInitialMax(max)
      .setMaxRenderedLength(90)
      .build()
  }

  override def startContainer(f: StartContainerCmd => StartContainerCmd): Unit = {
    logger.debug("startContainer --- start")
    val configureFunction: StartContainerCmd => StartContainerCmd =
      cmdConfigures.map(_.startContainerCmdConfigure).getOrElse(identity)
    f(configureFunction(newStartContainerCmd())).exec()
    logger.debug("startContainer --- finish")
  }

  override def stopContainer(f: StopContainerCmd => StopContainerCmd): Unit = {
    logger.debug("stopContainer --- start")
    val configureFunction: StopContainerCmd => StopContainerCmd =
      cmdConfigures.map(_.stopContainerCmdConfigure).getOrElse(identity)
    f(configureFunction(newStopContainerCmd())).exec()
    logger.debug("stopContainer --- finish")
  }

  override def awaitCondition(duration: Duration)(predicate: Option[Frame] => Boolean): Unit = {
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
          if (
            !terminate && {
              val frameOpt = Option(frameQueue.poll(outputFrameInterval.toMillis, TimeUnit.MILLISECONDS))
              frameOpt.foreach { frame =>
                logger.debug(frame.toString)
              }
              !predicate(frameOpt)
            }
          ) {
            loop()
          }
        }
        try {
          loop()
        } catch {
          case _: InterruptedException =>
            logger.debug("interrupted")
          case ex: Throwable =>
            logger.debug("occurred error", ex)
            throw ex
        }
      }
    }

    val thread = new Thread(waiter)
    thread.start()
    if (duration.isFinite) {
      val timer = new Timer()
      timer.schedule(
        new TimerTask {
          override def run(): Unit = {
            terminate = true
            thread.interrupt()
          }
        },
        duration.toMillis
      )
      timer.cancel()
    }
    thread.join()
    logger.debug("awaitCompletion --- finish")
  }

  override def createNetwork(
      name: String,
      f: CreateNetworkCmd => CreateNetworkCmd = identity
  ): CreateNetworkResponse = {
    f(dockerClient.createNetworkCmd().withName(name)).exec()
  }

  override def removeNetwork(id: String, f: RemoveNetworkCmd => RemoveNetworkCmd): Unit = {
    f(dockerClient.removeNetworkCmd(id)).exec()
  }

  override def dispose(): Unit = synchronized {
    logger.debug("dispose: start")
    if (containerId.isDefined) {
      removeContainer()
      if (isDockerClientAutoClose)
        dockerClient.close()
    }
    logger.debug("dispose: finish")
  }

}
