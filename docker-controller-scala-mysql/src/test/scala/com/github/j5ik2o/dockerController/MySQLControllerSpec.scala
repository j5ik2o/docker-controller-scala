package com.github.j5ik2o.dockerController

import com.github.j5ik2o.dockerController.flyway.{ FlywayConfig, FlywaySpecSupport }
import com.github.j5ik2o.dockerController.mysql.MySQLController
import org.scalatest.freespec.AnyFreeSpec

import java.sql.{ Connection, DriverManager, ResultSet, Statement }
import scala.concurrent.duration.{ Duration, DurationInt }
import scala.util.control.NonFatal

class MySQLControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport with FlywaySpecSupport {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt
  logger.debug(s"testTimeFactor = $testTimeFactor")

  val hostPort: Int        = temporaryServerPort()
  val rootPassword: String = "test"

  override protected def flywayDriverClassName: String = classOf[com.mysql.cj.jdbc.Driver].getName
  override protected def flywayDbHost: String          = dockerHost
  override protected def flywayDbHostPort: Int         = hostPort
  override protected def flywayDbName: String          = "test"
  override protected def flywayDbUserName: String      = "root"
  override protected def flywayDbPassword: String      = rootPassword

  override protected def flywayJDBCUrl: String =
    s"jdbc:mysql://$flywayDbHost:$flywayDbHostPort/$flywayDbName?useSSL=false&user=$flywayDbUserName&password=$flywayDbPassword"

  val controller: MySQLController = MySQLController(dockerClient)(hostPort, rootPassword, databaseName = Some("test"))

  override protected val dockerControllers: Vector[DockerController] = Vector(controller)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] =
    Map(
      controller -> WaitPredicateSetting(
        Duration.Inf,
        WaitPredicates.forListeningHostTcpPort(
          dockerHost,
          hostPort,
          (1 * testTimeFactor).seconds,
          Some((15 * testTimeFactor).seconds)
        )
      )
    )

  override protected def afterStartContainers(): Unit = {
    val flywayContext = createFlywayContext(FlywayConfig(Seq("flyway")))
    flywayContext.flyway.migrate()
  }

  "MySQLController" - {
    "run" in {
      var conn: Connection     = null
      var stmt: Statement      = null
      var resultSet: ResultSet = null
      try {
        Class.forName(flywayDriverClassName)
        conn = DriverManager.getConnection(flywayJDBCUrl)
        stmt = conn.createStatement
        val result = stmt.executeUpdate("INSERT INTO users VALUES(1, 'kato')")
        assert(result == 1)
        resultSet = stmt.executeQuery("SELECT * FROM users")
        while (resultSet.next()) {
          val id   = resultSet.getInt("id")
          val name = resultSet.getString("name")
          println(s"id = $id, name = $name")
        }
      } catch {
        case NonFatal(ex) =>
          ex.printStackTrace()
          fail("occurred error", ex)
      } finally {
        if (resultSet != null)
          resultSet.close()
        if (stmt != null)
          stmt.close()
        if (conn != null)
          conn.close()
      }
    }
  }

}
