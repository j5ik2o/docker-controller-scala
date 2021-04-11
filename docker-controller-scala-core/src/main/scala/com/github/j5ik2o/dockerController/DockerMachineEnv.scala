package com.github.j5ik2o.dockerController

import org.slf4j.LoggerFactory

import scala.sys.process._
import scala.util.Try
import scala.util.matching.Regex

case class DockerMachineEnv(tlsVerify: Boolean, dockerHost: String, dockerCertPath: String) {
  require(dockerHost != null)
  require(dockerCertPath != null)
}

object DockerMachineEnv {
  private val logger = LoggerFactory.getLogger(getClass)

  private val tlsVerifyRegex: Regex = """export DOCKER_TLS_VERIFY="(.*)"""".r

  private val hostRegex: Regex = """export DOCKER_HOST="(.*)"""".r

  private val certPathRegex: Regex = """export DOCKER_CERT_PATH="(.*)"""".r

  private def getDockerMachineCmd = Try {
    Seq("which", "docker-machine").!!.stripSuffix("\n")
  }

  def isSupportDockerMachine: Boolean = getDockerMachineCmd.isSuccess

  private def getDockerMachineEnv(name: String): Try[Vector[String]] = {
    for {
      cmd <- getDockerMachineCmd
      result <- Try {
        Seq(cmd, "env", name).!!.split("\n").toVector
      }
    } yield result
  }

  private def getDockerTlsVerify(env: Vector[String]): Boolean = {
    env
      .map {
        case tlsVerifyRegex(bs) if bs == "1" => Some(true)
        case tlsVerifyRegex(bs) if bs == "0" => Some(false)
        case _                               => None
      }.find(_.nonEmpty).flatten.get
  }

  private def getDockerHost(env: Vector[String]): String = {
    env
      .map {
        case hostRegex(v) => Some(v)
        case _            => None
      }.find(_.nonEmpty).flatten.get
  }

  private def getDockerCertPath(env: Vector[String]): String = {
    env
      .map {
        case certPathRegex(v) => Some(v)
        case _                => None
      }.find(_.nonEmpty).flatten.get
  }

  def load(name: String): Try[DockerMachineEnv] = {
    val envTry = getDockerMachineEnv(name)
    val result = envTry.map { env =>
      val tlsVerify = getDockerTlsVerify(env)
      val host      = getDockerHost(env)
      val certPath  = getDockerCertPath(env)
      DockerMachineEnv(tlsVerify, host, certPath)
    }
    result
  }

}
