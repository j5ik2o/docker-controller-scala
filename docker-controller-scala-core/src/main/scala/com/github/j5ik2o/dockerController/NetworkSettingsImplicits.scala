package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.model.{ ExposedPort, NetworkSettings, Ports }

import scala.jdk.CollectionConverters._
import scala.language.implicitConversions

final class NetworkSettingsOps(val networkSettings: NetworkSettings) extends AnyVal {

  def ports: Ports = {
    networkSettings.getPorts
  }

  def portBindings: Map[ExposedPort, Vector[Ports.Binding]] = {
    ports.getBindings.asScala.map { case (k, v) => k -> v.toVector }.toMap
  }

  def portBinding(exposedPort: ExposedPort): Option[Vector[Ports.Binding]] = {
    portBindings.get(exposedPort)
  }

  def bindingHostPorts(exposedPort: ExposedPort): Option[Vector[Int]] = {
    portBinding(exposedPort).map(_.map(_.getHostPortSpec.toInt))
  }

  def bindingHostTcpPorts(exposedPort: Int): Option[Vector[Int]] = {
    bindingHostPorts(ExposedPort.tcp(exposedPort))
  }

  def bindingHostUdpPorts(exposedPort: Int): Option[Vector[Int]] = {
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

}

trait NetworkSettingsImplicits {

  implicit def toNetworkSettingsOps(networkSettings: NetworkSettings): NetworkSettingsOps =
    new NetworkSettingsOps(networkSettings)

}

object NetworkSettingsImplicits extends NetworkSettingsImplicits
