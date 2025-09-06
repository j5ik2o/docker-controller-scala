import Dependencies._

def crossScalacOptions(scalaVersion: String): Seq[String] = CrossVersion.partialVersion(scalaVersion) match {
  case Some((3L, _)) =>
    Seq(
      "-source:3.0-migration",
      "-Xignore-scala2-macros"
    )
  case Some((2L, scalaMajor)) if scalaMajor >= 12 =>
    Seq(
      "-Ydelambdafy:method",
      "-target:jvm-17",
      "-Yrangepos",
      "-release:17"
      // "-Ywarn-unused"
    )
}

lazy val baseSettings = Seq(
  organization := "com.github.j5ik2o",
  homepage := Some(url("https://github.com/j5ik2o/docker-controller-scala")),
  licenses := List("The MIT License" -> url("http://opensource.org/licenses/MIT")),
  developers := List(
    Developer(
      id = "j5ik2o",
      name = "Junichi Kato",
      email = "j5ik2o@gmail.com",
      url = url("https://blog.j5ik2o.me")
    )
  ),
  scalaVersion := Versions.scala3Version,
  crossScalaVersions := Seq(Versions.scala212Version, Versions.scala213Version, Versions.scala3Version),
  javacOptions ++= Seq("-source", "17", "-target", "17"),
  scalacOptions ++= (Seq(
    "-unchecked",
    "-feature",
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-language:_"
  ) ++ crossScalacOptions(scalaVersion.value)),
  resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
  resolvers ++= Resolver.sonatypeOssRepos("releases"),
  resolvers += "Seasar Repository" at "https://maven.seasar.org/maven2/",
  libraryDependencies ++= Seq(
    scalatest.scalatest % Test
  ),
  dependencyOverrides ++= Seq(
    fasterxml.jacksonModuleScala
  ),
  Test / publishArtifact := false,
  Test / fork := true,
  Test / parallelExecution := false,
  Compile / doc / sources := {
    val old = (Compile / doc / sources).value
    if (scalaVersion.value == Versions.scala3Version) {
      Nil
    } else {
      old
    }
  },
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision
)

val `docker-controller-scala-core` = (project in file("docker-controller-scala-core"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-core",
    libraryDependencies ++= Seq(
      slf4j.api,
      dockerJava.dockerJava,
      dockerJava.dockerJavaTransportJersey,
      dockerJava.dockerJavaTransportHttpclient5,
      dockerJava.dockerJavaTransportOkhttp,
      tongfei.progressbar,
      seasar.s2util,
      freemarker.freemarker,
      logback.classic % Test,
      commons.io
    ),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3L, _)) =>
          Seq.empty
        case Some((2L, scalaMajor)) if scalaMajor == 13 =>
          Seq.empty
        case Some((2L, scalaMajor)) if scalaMajor == 12 =>
          Seq(
            scalaLang.scalaCollectionCompat
          )
      }
    }
  )

val `docker-controller-scala-scalatest` = (project in file("docker-controller-scala-scalatest"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-scalatest",
    libraryDependencies ++= Seq(
      scalatest.scalatest,
      logback.classic
    )
  ).dependsOn(`docker-controller-scala-core`)

val `docker-controller-scala-dynamodb-local` = (project in file("docker-controller-scala-dynamodb-local"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-dynamodb-local",
    libraryDependencies ++= Seq(
      scalatest.scalatest % Test,
      logback.classic     % Test,
      amazonAws.dynamodb  % Test
    )
  ).dependsOn(`docker-controller-scala-core`, `docker-controller-scala-scalatest` % Test)

val `docker-controller-scala-minio` = (project in file("docker-controller-scala-minio"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-minio",
    libraryDependencies ++= Seq(
      scalatest.scalatest % Test,
      logback.classic     % Test,
      amazonAws.s3        % Test
    )
  ).dependsOn(`docker-controller-scala-core`, `docker-controller-scala-scalatest` % Test)

val `docker-controller-scala-zookeeper` = (project in file("docker-controller-scala-zookeeper"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-zookeeper",
    libraryDependencies ++= Seq(
      scalatest.scalatest        % Test,
      logback.classic            % Test,
      apache.zooKeeper.zooKeeper % Test
    )
  ).dependsOn(`docker-controller-scala-core`, `docker-controller-scala-scalatest` % Test)

val `docker-controller-scala-kafka` = (project in file("docker-controller-scala-kafka"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-kafka",
    libraryDependencies ++= Seq(
      scalatest.scalatest       % Test,
      logback.classic           % Test,
      apache.kafka.kafkaClients % Test
    )
  ).dependsOn(
    `docker-controller-scala-core`,
    `docker-controller-scala-zookeeper`,
    `docker-controller-scala-scalatest` % Test
  )

val `docker-controller-scala-flyway` = (project in file("docker-controller-scala-flyway"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-flyway",
    libraryDependencies ++= Seq(
      "org.flywaydb"      % "flyway-core"                % "11.12.0",
      "org.flywaydb"      % "flyway-mysql"               % "11.12.0",
      "org.flywaydb"      % "flyway-database-postgresql" % "11.12.0",
      scalatest.scalatest % Test,
      logback.classic     % Test
    )
  ).dependsOn(`docker-controller-scala-core`, `docker-controller-scala-scalatest` % Test)

val `docker-controller-scala-mysql` = (project in file("docker-controller-scala-mysql"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-mysql",
    libraryDependencies ++= Seq(
      scalatest.scalatest % Test,
      logback.classic     % Test,
      mysql.connectorJava % Test
    )
  ).dependsOn(
    `docker-controller-scala-core`,
    `docker-controller-scala-scalatest` % Test,
    `docker-controller-scala-flyway`    % Test
  )

val `docker-controller-scala-postgresql` = (project in file("docker-controller-scala-postgresql"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-postgresql",
    libraryDependencies ++= Seq(
      scalatest.scalatest   % Test,
      logback.classic       % Test,
      postgresql.postgresql % Test
    )
  ).dependsOn(
    `docker-controller-scala-core`,
    `docker-controller-scala-scalatest` % Test,
    `docker-controller-scala-flyway`    % Test
  )

val `docker-controller-scala-redis` = (project in file("docker-controller-scala-redis"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-redis",
    libraryDependencies ++= Seq(
      scalatest.scalatest    % Test,
      logback.classic        % Test,
      (debasishg.redisClient % Test).cross(CrossVersion.for3Use2_13)
    )
  ).dependsOn(`docker-controller-scala-core`, `docker-controller-scala-scalatest` % Test)

val `docker-controller-scala-memcached` = (project in file("docker-controller-scala-memcached"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-memcached",
    libraryDependencies ++= Seq(
      scalatest.scalatest       % Test,
      logback.classic           % Test,
      (twitter.finagleMemcached % Test).cross(CrossVersion.for3Use2_13)
    )
  ).dependsOn(`docker-controller-scala-core`, `docker-controller-scala-scalatest` % Test)

val `docker-controller-scala-elasticmq` = (project in file("docker-controller-scala-elasticmq"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-elasticmq",
    libraryDependencies ++= Seq(
      scalatest.scalatest % Test,
      logback.classic     % Test,
      amazonAws.sqs       % Test
    )
  ).dependsOn(`docker-controller-scala-core`, `docker-controller-scala-scalatest` % Test)

val `docker-controller-scala-elasticsearch` = (project in file("docker-controller-scala-elasticsearch"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-elasticsearch",
    libraryDependencies ++= Seq(
      scalatest.scalatest               % Test,
      logback.classic                   % Test,
      elasticsearch.restHighLevelClient % Test,
      "co.elastic.clients"              % "elasticsearch-java" % "7.17.29" % Test,
      "com.fasterxml.jackson.core"      % "jackson-databind"   % "2.17.2"  % Test,
      "org.apache.logging.log4j"        % "log4j-api"          % "2.23.1"  % Test,
      "org.apache.logging.log4j"        % "log4j-core"         % "2.23.1"  % Test
    )
  ).dependsOn(`docker-controller-scala-core`, `docker-controller-scala-scalatest` % Test)

val `docker-controller-scala-localstack` = (project in file("docker-controller-scala-localstack"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-localstack",
    libraryDependencies ++= Seq(
      scalatest.scalatest % Test,
      logback.classic     % Test,
      amazonAws.s3        % Test,
      amazonAws.dynamodb  % Test
    )
  ).dependsOn(`docker-controller-scala-core`, `docker-controller-scala-scalatest` % Test)

val `docker-controller-scala-root` = (project in file("."))
  .settings(baseSettings)
  .settings(name := "docker-controller-scala-root")
  .aggregate(
    `docker-controller-scala-core`,
    `docker-controller-scala-scalatest`,
    `docker-controller-scala-flyway`,
    // for RDBMS
    `docker-controller-scala-mysql`,
    `docker-controller-scala-postgresql`,
    // for NoSQL
    `docker-controller-scala-redis`,
    `docker-controller-scala-memcached`,
    `docker-controller-scala-elasticsearch`,
    `docker-controller-scala-kafka`,
    `docker-controller-scala-zookeeper`,
    // AWS
    `docker-controller-scala-dynamodb-local`,
    `docker-controller-scala-minio`,
    `docker-controller-scala-localstack`,
    `docker-controller-scala-elasticmq`
  )

// --- Custom commands
addCommandAlias("lint", ";scalafmtCheck;test:scalafmtCheck;scalafmtSbtCheck;scalafixAll --check")
addCommandAlias("fmt", ";scalafmtAll;scalafmtSbt")
