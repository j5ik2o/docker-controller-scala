import sbt._

object Dependencies {

  object Versions {
    val scala212Version              = "2.12.13"
    val scala213Version              = "2.13.5"
    val scalaTestVersion             = "3.2.8"
    val logbackVersion               = "1.2.3"
    val scalaCollectionCompatVersion = "2.4.3"
    val dockerJavaVersion            = "3.2.8"
    val progressBarVersion           = "0.9.1"
    val enumeratumVersion            = "1.6.1"
  }

  object scalaLang {

    val scalaCollectionCompat =
      "org.scala-lang.modules" %% "scala-collection-compat" % Versions.scalaCollectionCompatVersion

  }

  object scalatest {
    val scalatest = "org.scalatest" %% "scalatest" % Versions.scalaTestVersion
  }

  object slf4j {
    val api = "org.slf4j" % "slf4j-api" % "1.7.30"

  }

  object amazonAws {
    val dynamodb = "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.1004"
    val s3       = "com.amazonaws" % "aws-java-sdk-s3"       % "1.11.1004"
  }

  object apache {

    object zooKeeper {
      val zooKeeper = "org.apache.zookeeper" % "zookeeper" % "3.7.0"
    }

    object kafka {
      val kafkaClients = "org.apache.kafka" % "kafka-clients" % "2.8.0"
    }
  }

  object mysql {
    val connectorJava = "mysql" % "mysql-connector-java" % "8.0.24"
  }

  object elasticsearch {
    val restHighLevelClient = "org.elasticsearch.client" % "elasticsearch-rest-high-level-client" % "7.12.0"
  }

  object dockerJava {

    val dockerJava = "com.github.docker-java" % "docker-java" % Versions.dockerJavaVersion

    val dockerJavaTransportJersey =
      "com.github.docker-java" % "docker-java-transport-jersey" % Versions.dockerJavaVersion

    val dockerJavaTransportHttpclient5 =
      "com.github.docker-java" % "docker-java-transport-httpclient5" % Versions.dockerJavaVersion

    val dockerJavaTransportOkhttp =
      "com.github.docker-java" % "docker-java-transport-okhttp" % Versions.dockerJavaVersion

  }

  object tongfei {
    val progressbar = "me.tongfei" % "progressbar" % Versions.progressBarVersion

  }

  object seasar {
    val s2util = "org.seasar.util" % "s2util" % "0.0.1"

  }

  object freemarker {
    val freemarker = "org.freemarker" % "freemarker" % "2.3.31"

  }

  object logback {
    val classic = "ch.qos.logback" % "logback-classic" % Versions.logbackVersion

  }

  object commons {
    val io = "commons-io" % "commons-io" % "2.8.0"
  }

  object beachape {
    val enumeratum = "com.beachape" %% "enumeratum" % Versions.enumeratumVersion

  }

}
