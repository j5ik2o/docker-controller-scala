package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.model.Frame
import org.slf4j.{ Logger, LoggerFactory }

import java.net.{ HttpURLConnection, InetSocketAddress, Socket, URL }
import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import scala.util.control.NonFatal
import scala.util.matching.Regex

object WaitPredicates {

  type WaitPredicate = Frame => Boolean

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  def forLogMessageExactly(text: String): WaitPredicate = { frame => new String(frame.getPayload) == text }

  def forLogMessageContained(text: String): WaitPredicate = { frame => new String(frame.getPayload).contains(text) }

  def forLogMessageByRegex(regex: Regex): WaitPredicate = { frame =>
    regex.findFirstIn(new String(frame.getPayload)).isDefined
  }

  def forListeningHostTcpPort(
      host: String,
      hostPort: Int,
      timeout: FiniteDuration = 500.milliseconds
  ): WaitPredicate = { _: Frame =>
    val s: Socket = new Socket()
    try {
      logger.debug("try: Socket#connect ...")
      s.connect(new InetSocketAddress(host, hostPort), timeout.toMillis.toInt)
      val result = s.isConnected
      logger.debug(s"connected: Socket#connect, result = $result")
      result
    } catch {
      case NonFatal(ex) =>
        logger.debug("occurred error", ex)
        false
    } finally {
      if (s != null)
        s.close()
    }
  }

  def forListeningHttpPort(host: String, hostPort: Int): WaitPredicate = { _: Frame =>
    forListeningHttp(host, hostPort).isDefined
  }

  def forListeningHttpPortWithPredicate(host: String, hostPort: Int)(p: HttpURLConnection => Boolean): WaitPredicate = {
    _: Frame => forListeningHttp(host, hostPort).exists(p)
  }

  def forListeningHttpPortWithStatusOK(host: String, hostPort: Int): WaitPredicate = {
    forListeningHttpPortWithPredicate(host, hostPort)(_.getResponseCode == 200)
  }

  private def forListeningHttp(host: String, hostPort: Int): Option[HttpURLConnection] = {
    var connection: HttpURLConnection = null
    try {
      val url = new URL(s"http://$host:$hostPort")
      logger.debug("try: HttpURLConnection#openConnection ...")
      connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")
      connection.connect()
      logger.debug("connected: HttpURLConnection#openConnection")
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
