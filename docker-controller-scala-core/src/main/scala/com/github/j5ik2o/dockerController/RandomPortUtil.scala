package com.github.j5ik2o.dockerController

import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel

trait RandomPortUtil {

  def temporaryServerAddress(interface: String = "127.0.0.1"): InetSocketAddress = RandomPortUtil.synchronized {
    val serverSocket = ServerSocketChannel.open()
    try {
      serverSocket.socket.bind(new InetSocketAddress(interface, 0))
      val port = serverSocket.socket.getLocalPort
      new InetSocketAddress(interface, port)
    } finally serverSocket.close()
  }

  def temporaryServerHostnameAndPort(interface: String = "127.0.0.1"): (String, Int) = RandomPortUtil.synchronized {
    val socketAddress = temporaryServerAddress(interface)
    socketAddress.getHostName -> socketAddress.getPort
  }

  def temporaryServerPort(interface: String = "127.0.0.1"): Int =
    temporaryServerHostnameAndPort(interface)._2
}

object RandomPortUtil extends RandomPortUtil
