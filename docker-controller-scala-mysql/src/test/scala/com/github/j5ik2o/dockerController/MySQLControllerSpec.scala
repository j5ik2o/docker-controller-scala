package com.github.j5ik2o.dockerController

import com.github.j5ik2o.dockerController.mysql.MySQLController
import org.scalatest.freespec.AnyFreeSpec

import java.sql.{ Connection, DriverManager, ResultSet, Statement }
import scala.concurrent.duration.Duration

class MySQLControllerSpec extends AnyFreeSpec with DockerControllerSpecSupport {
  val hostPort: Int                                                  = RandomPortUtil.temporaryServerPort()
  val rootPassword: String                                           = "test"
  val controller: MySQLController                                    = MySQLController(dockerClient)(hostPort, rootPassword, databaseName = Some("test"))
  override protected val dockerControllers: Vector[DockerController] = Vector(controller)

  override protected val waitPredicatesSettings: Map[DockerController, WaitPredicateSetting] =
    Map(
      controller -> WaitPredicateSetting(Duration.Inf, WaitPredicates.forListeningHostTcpPort(dockerHost, hostPort))
    )

  "MySQLController" - {
    "run" in {
      var conn: Connection     = null
      var stmt: Statement      = null
      var resultSet: ResultSet = null
      try {
        Class.forName("com.mysql.cj.jdbc.Driver")
        conn = DriverManager.getConnection(
          s"jdbc:mysql://$dockerHost:$hostPort/test?user=root&password=$rootPassword"
        )
        stmt = conn.createStatement
        resultSet = stmt.executeQuery("SELECT 1 FROM DUAL")
        assert(resultSet.next())
        assert(resultSet.getInt(1) == 1)
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
