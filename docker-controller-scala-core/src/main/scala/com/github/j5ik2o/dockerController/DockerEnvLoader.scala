package com.github.j5ik2o.dockerController

import com.github.dockerjava.core.DefaultDockerClientConfig

import scala.sys.process.Process
import scala.util.Try
import scala.util.matching.Regex

case class DockerEnv(tlsVerify: Boolean, dockerHost: String, dockerCertPath: String)

object DockerEnvLoader {
  val tlsVerifyRegex: Regex = """export DOCKER_TLS_VERIFY="(.*)"""".r

  val hostRegex: Regex = """export DOCKER_HOST="tcp://(.*)""".r

  val certPathRegex: Regex = """export DOCKER_CERT_PATH="(.*)"""".r

  private def getDockerMachineEnv: Try[Vector[String]] = Try {
    Process("docker-machine env").lazyLines.toVector
  }

  private def getTlsVerify(env: Vector[String]): Boolean = {
    env(0) match {
      case tlsVerifyRegex(s) => s.toBoolean
    }
  }

  private def getHost(env: Vector[String]): String = {
    env(1) match {
      case hostRegex(s) => s
    }
  }

  private def getCertPath(env: Vector[String]): String = {
    env(2) match {
      case certPathRegex(s) => s
    }
  }

  def load(): Try[DockerEnv] = {
    getDockerMachineEnv.map { env =>
      val tlsVerify = getTlsVerify(env)
      val host      = getHost(env)
      val certPath  = getCertPath(env)
      DockerEnv(tlsVerify, host, certPath)
    }
  }

}
