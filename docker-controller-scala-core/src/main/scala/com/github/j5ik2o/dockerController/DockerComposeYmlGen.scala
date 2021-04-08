package com.github.j5ik2o.dockerController

import freemarker.template.Configuration
import org.seasar.util.io.ResourceUtil

import java.io.{ File, FileWriter }
import java.util.Locale
import scala.jdk.CollectionConverters.MapHasAsJava

object DockerComposeYmlGen {

  val cfg = new Configuration(Configuration.VERSION_2_3_29)
  cfg.setDefaultEncoding("UTF-8")
  cfg.setLocale(Locale.ENGLISH)
  cfg.setNumberFormat("computer")

  def generate(ymlFtl: String, context: Map[String, AnyRef], outputFile: File): Unit = {
    val ymlFtlFile: File = ResourceUtil.getResourceAsFile(ymlFtl)
    cfg.setDirectoryForTemplateLoading(ymlFtlFile.getParentFile)
    val tmpl = cfg.getTemplate(ymlFtlFile.getName)
    val out  = new FileWriter(outputFile)
    try tmpl.process(context.asJava, out)
    finally out.close()
  }

}
