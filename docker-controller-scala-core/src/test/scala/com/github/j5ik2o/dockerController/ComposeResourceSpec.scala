package com.github.j5ik2o.dockerController

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerCmd
import org.apache.commons.io.FileUtils
import org.scalatest.freespec.AnyFreeSpec

import java.io.{ FileNotFoundException, IOException }
import java.lang.reflect.{ InvocationHandler, Method, Proxy }
import java.net.{ URL, URLClassLoader }
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, Path }
import java.util.jar.{ JarEntry, JarOutputStream }
import scala.collection.mutable

class ComposeResourceSpec extends AnyFreeSpec {
  private def fixture(f: Path => Unit): Unit = {
    val root = Files.createTempDirectory("compose-resource-")
    try f(root)
    finally FileUtils.deleteDirectory(root.toFile)
  }

  private def write(root: Path, name: String, value: String): Path = {
    val path = root.resolve(name)
    Files.createDirectories(path.getParent)
    Files.write(path, value.getBytes(UTF_8))
  }

  private def withLoader(loader: ClassLoader)(f: => Unit): Unit = {
    val thread   = Thread.currentThread()
    val previous = thread.getContextClassLoader
    thread.setContextClassLoader(loader)
    try f
    finally thread.setContextClassLoader(previous)
  }

  private def generate(name: String, root: Path): String = {
    val output = root.resolve("output.yml")
    DockerComposeFileGen.generate(name, Map("message" -> "rendered"), output.toFile)
    new String(Files.readAllBytes(output), UTF_8)
  }

  "file templates retain relative includes and URI characters including plus" in fixture { root =>
    val directory = "space 日本語 %25 #?+"
    write(root, s"$directory/part.ftl", "included ")
    write(root, s"$directory/main+.ftl", "<#include \"part.ftl\">${message}")
    val loader = new URLClassLoader(Array(root.toUri.toURL), null)
    try
      withLoader(loader) {
        assert(generate(s"$directory/main+.ftl", root) == "included rendered")
      }
    finally loader.close()
  }

  "resource names pass unchanged to the context loader without a filesystem fallback" in fixture { root =>
    val template = write(root, "actual.ftl", "${message}")
    val names    = mutable.ArrayBuffer.empty[String]
    val loader = new ClassLoader(null) {
      override def getResource(name: String): URL = {
        names += name
        if (name == "/alias.ftl") template.toUri.toURL else null
      }
    }
    withLoader(loader) {
      assert(generate("/alias.ftl", root) == "rendered")
      intercept[FileNotFoundException](generate(template.toString, root))
    }
    assert(names.toSeq == Seq("/alias.ftl", template.toString))
  }

  "missing resources and directories fail with FileNotFoundException" in fixture { root =>
    val loader = new URLClassLoader(Array(root.toUri.toURL), null)
    try
      withLoader(loader) {
        intercept[FileNotFoundException](generate("missing.ftl", root))
        Files.createDirectory(root.resolve("directory"))
        intercept[FileNotFoundException](generate("directory", root))
      }
    finally loader.close()
  }

  "empty and null names and a null context loader fail with IllegalArgumentException" in fixture { root =>
    intercept[IllegalArgumentException](generate("", root))
    intercept[IllegalArgumentException](generate(null, root))
    withLoader(null) {
      intercept[IllegalArgumentException](generate("template.ftl", root))
    }
  }

  "JAR resources fail explicitly without extraction" in fixture { root =>
    val jar    = root.resolve("templates.jar")
    val output = new JarOutputStream(Files.newOutputStream(jar))
    try {
      output.putNextEntry(new JarEntry("template.ftl"))
      output.write("${message}".getBytes(UTF_8))
      output.closeEntry()
    } finally output.close()
    val loader = new URLClassLoader(Array(jar.toUri.toURL), null)
    try
      withLoader(loader) {
        val error = intercept[IOException](generate("template.ftl", root))
        assert(error.getMessage.contains("Compose requires a file resource"))
        assert(!Files.exists(root.resolve("output.yml")))
      }
    finally loader.close()
  }

  "Compose command copies YAML and env basenames, overwrites, and renders env templates" in fixture { root =>
    write(root, "source/compose+.yml", "services: {}")
    write(root, "first/settings.env", "FIRST=1")
    write(root, "second/settings.env", "SECOND=2")
    write(root, "vars.env.ftl", "MESSAGE=${message}")
    val working = Files.createDirectory(root.resolve("working"))
    write(working, "compose+.yml", "old yaml")
    write(working, "settings.env", "old env")
    val calls = mutable.Map.empty[String, Seq[AnyRef]]
    val command = Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[CreateContainerCmd]),
        new InvocationHandler {
          override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {
            assert(Set("withCmd", "withEnv", "withWorkingDir", "withHostConfig").contains(method.getName))
            calls(method.getName) = args.toSeq
            proxy.asInstanceOf[AnyRef]
          }
        }
      ).asInstanceOf[CreateContainerCmd]
    val client = Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[DockerClient]),
        new InvocationHandler {
          override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {
            assert(method.getName == "createContainerCmd")
            assert(args.toSeq == Seq("docker/compose:1.24.1"))
            command
          }
        }
      ).asInstanceOf[DockerClient]
    val loader = new URLClassLoader(Array(root.toUri.toURL), null)
    val stop   = new RuntimeException("stop before Docker exec")
    try
      withLoader(loader) {
        val controller = DockerComposeController(client)(
          working.toFile,
          "source/compose+.yml",
          Seq("first/settings.env", "second/settings.env", "vars.env.ftl"),
          Map("message" -> "rendered")
        )
        assert(intercept[RuntimeException](controller.createContainer(_ => throw stop)) eq stop)
      }
    finally loader.close()
    assert(new String(Files.readAllBytes(working.resolve("compose+.yml")), UTF_8) == "services: {}")
    assert(new String(Files.readAllBytes(working.resolve("settings.env")), UTF_8) == "SECOND=2")
    val files = working.toFile.listFiles().toSeq
    val env   = files.find(_.getName.matches("vars-.+\\.env")).get
    assert(new String(Files.readAllBytes(env.toPath), UTF_8) == "MESSAGE=rendered")
    assert(calls("withCmd").head.asInstanceOf[Array[String]].toSeq == Seq("up"))
    assert(
      calls("withEnv").head.asInstanceOf[Array[String]].toSeq ==
        Seq("COMPOSE_FILE=compose+.yml", "DOCKER_HOST=unix:///docker.sock")
    )
    assert(calls("withWorkingDir") == Seq(working.toFile.getPath))
  }
}
