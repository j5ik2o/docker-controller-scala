package com.github.j5ik2o.dockerController

import freemarker.template.{ Configuration, TemplateExceptionHandler }

import java.io.{ File, FileWriter }
import java.util.Locale
import scala.jdk.CollectionConverters._

object DockerComposeFileGen {

  final val FreemarkerVersion = Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS

  private val cfg = new Configuration(FreemarkerVersion)

  cfg.setDefaultEncoding("UTF-8")
  cfg.setLocale(Locale.ENGLISH)
  cfg.setNumberFormat("computer")
  cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER)
  cfg.setLogTemplateExceptions(false)
  cfg.setWrapUncheckedExceptions(true)
  cfg.setFallbackOnNullLoopVariable(false)

  def generate(ymlFtl: String, context: Map[String, AnyRef], outputFile: File): Unit = {
    val ymlFtlFile: File = ComposeResourceFile.resolve(ymlFtl)
    cfg.setDirectoryForTemplateLoading(ymlFtlFile.getParentFile)
    val template = cfg.getTemplate(ymlFtlFile.getName)
    val writer   = new FileWriter(outputFile)
    try template.process(context.asJava, writer)
    finally writer.close()
  }

}
