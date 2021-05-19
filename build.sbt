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
      "-target:jvm-1.8",
      "-Yrangepos",
      "-Ywarn-unused"
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
  scalaVersion := Versions.scala213Version,
  crossScalaVersions := Seq(Versions.scala212Version, Versions.scala213Version, Versions.scala3Version),
  scalacOptions ++= (Seq(
    "-unchecked",
    "-feature",
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-language:_"
  ) ++ crossScalacOptions(scalaVersion.value)),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    "Seasar Repository" at "https://maven.seasar.org/maven2/"
  ),
  libraryDependencies ++= Seq(
    scalatest.scalatest % Test
  ),
  ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value),
  semanticdbEnabled := false,
  semanticdbVersion := scalafixSemanticdb.revision,
  Test / publishArtifact := false,
  Test / fork := true,
  testForkedParallel := true,
  concurrentRestrictions := {
    val par = parallelExecution.value
    val max = EvaluateTask.SystemProcessors
    List(
      Tags.limitAll(if (par) max else 1),
      Tags.limit(Tags.ForkedTestGroup, 2),
      Tags.exclusiveGroup(Tags.Clean)
    )
  }
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

val `docker-controller-scala-mysql` = (project in file("docker-controller-scala-mysql"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-mysql",
    libraryDependencies ++= Seq(
      scalatest.scalatest % Test,
      logback.classic     % Test,
      mysql.connectorJava % Test
    )
  ).dependsOn(`docker-controller-scala-core`, `docker-controller-scala-scalatest` % Test)

val `docker-controller-scala-elasticsearch` = (project in file("docker-controller-scala-elasticsearch"))
  .settings(baseSettings)
  .settings(
    name := "docker-controller-scala-elasticsearch",
    libraryDependencies ++= Seq(
      scalatest.scalatest               % Test,
      logback.classic                   % Test,
      elasticsearch.restHighLevelClient % Test
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
    `docker-controller-scala-mysql`,
    `docker-controller-scala-dynamodb-local`,
    `docker-controller-scala-minio`,
    `docker-controller-scala-zookeeper`,
    `docker-controller-scala-kafka`,
    `docker-controller-scala-elasticsearch`,
    `docker-controller-scala-localstack`
  )

// --- Custom commands
// addCommandAlias("lint", ";scalafmtCheck;test:scalafmtCheck;scalafmtSbtCheck;scalafixAll --check")
addCommandAlias("lint", ";scalafmtCheck;test:scalafmtCheck;scalafmtSbtCheck")
addCommandAlias("fmt", ";scalafmtAll;scalafmtSbt")
