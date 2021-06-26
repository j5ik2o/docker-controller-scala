package com.github.j5ik2o.dockerController.postgresql

import com.github.j5ik2o.dockerController.{
  DockerController,
  DockerControllerSpecSupport,
  RandomPortUtil,
  WaitPredicates
}
import org.scalatest.freespec.AnyFreeSpec

import java.sql.{ Connection, DriverManager, ResultSet, Statement }
import scala.concurrent.duration._
import scala.util.control.NonFatal

class PostgreSQLControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport {
  val testTimeFactor: Int = sys.env.getOrElse("TEST_TIME_FACTOR", "1").toInt
  logger.debug(s"testTimeFactor = $testTimeFactor")

  val hostPort: Int                = RandomPortUtil.temporaryServerPort()
  val rootUserName: String         = "postgres"
  val rootPassword: Option[String] = Some("test")

  val controller: PostgreSQLController                               = PostgreSQLController(dockerClient)(hostPort, rootUserName, rootPassword)
  override protected val dockerControllers: Vector[DockerController] = Vector(controller)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] =
    Map(
      controller -> WaitPredicateSetting(
        Duration.Inf,
        WaitPredicates.forListeningHostTcpPort(
          dockerHost,
          hostPort,
          (1 * testTimeFactor).seconds,
          Some((5 * testTimeFactor).seconds)
        )
      )
    )

  "MySQLController" - {
    "run" in {
      var conn: Connection     = null
      var stmt: Statement      = null
      var resultSet: ResultSet = null
      try {
        Class.forName("org.postgresql.Driver")
        conn = DriverManager.getConnection(
          s"jdbc:postgresql://$dockerHost:$hostPort/postgres",
          rootUserName,
          rootPassword.get
        )
        stmt = conn.createStatement
        resultSet = stmt.executeQuery("SELECT 1 FROM DUAL")
        while (resultSet.next())
          assert(resultSet.getInt(1) == 1)
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
