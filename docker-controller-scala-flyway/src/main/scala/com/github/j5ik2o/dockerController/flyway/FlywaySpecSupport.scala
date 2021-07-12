package com.github.j5ik2o.dockerController.flyway

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.callback.Callback
import org.flywaydb.core.internal.jdbc.DriverDataSource

import javax.sql.DataSource
import scala.jdk.CollectionConverters._

final case class PlaceholderConfig(
    placeholderReplacement: Boolean = false,
    placeholders: Map[String, String] = Map.empty,
    placeholderPrefix: Option[String] = None,
    placeholderSuffix: Option[String] = None
)

final case class FlywayConfig(
    locations: Seq[String],
    callbacks: Seq[Callback] = Seq.empty,
    placeholderConfig: Option[PlaceholderConfig] = None
)

final case class FlywayConfigWithDataSource(driverDataSource: DataSource, config: FlywayConfig)

final case class FlywayContext(flyway: Flyway, config: FlywayConfigWithDataSource)

trait FlywaySpecSupport {
  protected def flywayDriverClassName: String
  protected def flywayDbHost: String
  protected def flywayDbHostPort: Int
  protected def flywayDbName: String
  protected def flywayDbUserName: String
  protected def flywayDbPassword: String
  protected def flywayJDBCUrl: String

  private def createFlywayContext(flywayConfigWithDataSource: FlywayConfigWithDataSource): FlywayContext = {
    val configure = Flyway.configure()
    configure.dataSource(flywayConfigWithDataSource.driverDataSource)
    configure.locations(flywayConfigWithDataSource.config.locations: _*)
    configure.callbacks(flywayConfigWithDataSource.config.callbacks: _*)
    flywayConfigWithDataSource.config.placeholderConfig.foreach { pc =>
      configure.placeholderReplacement(pc.placeholderReplacement)
      configure.placeholders(pc.placeholders.asJava)
      pc.placeholderPrefix.foreach { pp =>
        configure.placeholderPrefix(pp)
      }
      pc.placeholderSuffix.foreach { ps =>
        configure.placeholderSuffix(ps)
      }
    }
    FlywayContext(configure.load(), flywayConfigWithDataSource)
  }

  private def createFlywayDataSource: DataSource = new DriverDataSource(
    getClass.getClassLoader,
    flywayDriverClassName,
    flywayJDBCUrl,
    flywayDbUserName,
    flywayDbPassword
  )

  // s"jdbc:mysql://$flywayDbHost:$flywayDbHostPort/$flywayDbName?useSSL=false&user=$flywayDbUserName&password=$flywayDbPassword",

  protected def createFlywayContext(flywayConfig: FlywayConfig): FlywayContext = createFlywayContext(
    FlywayConfigWithDataSource(createFlywayDataSource, flywayConfig)
  )
}
