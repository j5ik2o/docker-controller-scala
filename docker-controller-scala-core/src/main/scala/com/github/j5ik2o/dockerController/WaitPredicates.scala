package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.model.Frame
import org.slf4j.{ Logger, LoggerFactory }

import java.net.{ HttpURLConnection, InetSocketAddress, Socket, URL }
import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.util.control.NonFatal
import scala.util.matching.Regex

object WaitPredicates {

  type WaitPredicate = Option[Frame] => Boolean

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  def forDebug(
      awaitDurationOpt: Option[FiniteDuration] = Some(500.milliseconds)
  ): WaitPredicate = { frameOpt =>
    frameOpt.exists { frame =>
      val line = new String(frame.getPayload).stripLineEnd
      logger.debug(s"forDebug: line = $line")
      awaitDurationOpt.foreach { awaitDuration => Thread.sleep(awaitDuration.toMillis) }
      false
    }
  }

  def forLogMessageExactly(
      text: String,
      awaitDurationOpt: Option[FiniteDuration] = Some(500.milliseconds)
  ): WaitPredicate = { frameOpt =>
    frameOpt.exists { frame =>
      val line   = new String(frame.getPayload).stripLineEnd
      val result = line == text
      if (result) {
        logger.debug(s"forLogMessageExactly: result = $result, line = $line")
        awaitDurationOpt.foreach { awaitDuration => Thread.sleep(awaitDuration.toMillis) }
      }
      result
    }
  }

  def forLogMessageContained(
      text: String,
      awaitDurationOpt: Option[FiniteDuration] = Some(500.milliseconds)
  ): WaitPredicate = { frameOpt =>
    frameOpt.exists { frame =>
      val line   = new String(frame.getPayload).stripLineEnd
      val result = line.contains(text)
      if (result) {
        logger.debug(s"forLogMessageContained: result = $result, line = $line")
        awaitDurationOpt.foreach { awaitDuration => Thread.sleep(awaitDuration.toMillis) }
      }
      result
    }
  }

  def forLogMessageByRegex(
      regex: Regex,
      awaitDurationOpt: Option[FiniteDuration] = Some(500.milliseconds)
  ): WaitPredicate = { frameOpt =>
    frameOpt.exists { frame =>
      val line   = new String(frame.getPayload).stripLineEnd
      val result = regex.findFirstIn(line).isDefined
      if (result) {
        logger.debug(s"forLogMessageByRegex: result = $result, line = $line")
        awaitDurationOpt.foreach { awaitDuration => Thread.sleep(awaitDuration.toMillis) }
      }
      result
    }
  }

  def forListeningHostTcpPort(
      host: String,
      hostPort: Int,
      connectionTimeout: FiniteDuration = 500.milliseconds,
      awaitDurationOpt: Option[FiniteDuration] = Some(500.milliseconds)
  ): WaitPredicate = { frameOpt =>
    val line      = frameOpt.map(frame => new String(frame.getPayload)).getOrElse("")
    val s: Socket = new Socket()
    try {
      s.connect(new InetSocketAddress(host, hostPort), connectionTimeout.toMillis.toInt)
      val result = s.isConnected
      if (result) {
        logger.debug(s"forListeningHostTcpPort: result = $result, line = $line")
        awaitDurationOpt.foreach { awaitDuration => Thread.sleep(awaitDuration.toMillis) }
      }
      result
    } catch {
      case NonFatal(_) =>
        false
    } finally {
      if (s != null)
        s.close()
    }
  }

  def forListeningHttpPort(
      host: String,
      hostPort: Int,
      awaitDurationOpt: Option[FiniteDuration] = Some(500.milliseconds)
  ): WaitPredicate = { _ => forListeningHttp(host, hostPort, awaitDurationOpt).isDefined }

  def forListeningHttpPortWithPredicate(
      host: String,
      hostPort: Int,
      awaitDurationOpt: Option[FiniteDuration] = Some(500.milliseconds)
  )(p: HttpURLConnection => Boolean): WaitPredicate = { _ =>
    forListeningHttp(host, hostPort, awaitDurationOpt).exists(p)
  }

  def forListeningHttpPortWithStatusOK(
      host: String,
      hostPort: Int,
      awaitDurationOpt: Option[FiniteDuration] = Some(500.milliseconds)
  ): WaitPredicate = {
    forListeningHttpPortWithPredicate(host, hostPort, awaitDurationOpt)(_.getResponseCode == 200)
  }

  private def forListeningHttp(
      host: String,
      hostPort: Int,
      awaitDurationOpt: Option[FiniteDuration] = Some(500.milliseconds)
  ): Option[HttpURLConnection] = {
    var connection: HttpURLConnection = null
    try {
      val url = new URL(s"http://$host:$hostPort")
      logger.debug("try: HttpURLConnection#openConnection ...")
      connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")
      connection.connect()
      logger.debug("connected: HttpURLConnection#openConnection")
      awaitDurationOpt.foreach { awaitDuration => Thread.sleep(awaitDuration.toMillis) }
      Some(connection)
    } catch {
      case NonFatal(ex) =>
        logger.debug("occurred error", ex)
        None
    } finally {
      if (connection != null)
        connection.disconnect()
    }
  }

}
