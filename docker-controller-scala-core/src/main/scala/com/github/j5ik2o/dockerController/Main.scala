package com.github.j5ik2o.dockerController

import org.seasar.util.io.ResourceUtil

object Main extends App {
  val file1 = ResourceUtil.getBuildDir("docker-compose-1.yml")
  println(file1.getPath)
  val file2 = ResourceUtil.getResourceAsFile("docker-compose-1.yml")
  println(file2.getPath)
}
