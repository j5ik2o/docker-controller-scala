val scala212Version              = "2.12.13"
val scala213Version              = "2.13.5"
val scalaTestVersion             = "3.2.6"
val logbackVersion               = "1.2.3"
val scalaCollectionCompatVersion = "2.4.3"
val dockerJavaVersion            = "3.2.7"

def crossScalacOptions(scalaVersion: String): Seq[String] = CrossVersion.partialVersion(scalaVersion) match {
  case Some((2L, scalaMajor)) if scalaMajor >= 12 =>
    Seq.empty
  case Some((2L, scalaMajor)) if scalaMajor <= 11 =>
    Seq("-Yinline-warnings")
}

lazy val deploySettings = Seq(
  sonatypeProfileName := "com.github.j5ik2o",
  publishMavenStyle := true,
  Test / publishArtifact := false,
  pomIncludeRepository := { _ => false },
  pomExtra := {
    <url>https://github.com/j5ik2o/docker-controller-scala</url>
      <licenses>
        <license>
          <name>The MIT License</name>
          <url>http://opensource.org/licenses/MIT</url>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:j5ik2o/docker-controller-scala.git</url>
        <connection>scm:git:github.com/j5ik2o/docker-controller-scala</connection>
        <developerConnection>scm:git:git@github.com:j5ik2o/docker-controller-scala.git</developerConnection>
      </scm>
      <developers>
        <developer>
          <id>j5ik2o</id>
          <name>Junichi Kato</name>
        </developer>
      </developers>
  },
  publishTo := sonatypePublishToBundle.value,
  credentials := {
    val ivyCredentials = (LocalRootProject / baseDirectory).value / ".credentials"
    val gpgCredentials = (LocalRootProject / baseDirectory).value / ".gpgCredentials"
    Credentials(ivyCredentials) :: Credentials(gpgCredentials) :: Nil
  }
)

lazy val baseSettings = Seq(
  organization := "com.github.j5ik2o",
  scalaVersion := scala212Version,
  crossScalaVersions := Seq(scala212Version, scala213Version),
  scalacOptions ++= (Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-encoding",
      "UTF-8",
      "-language:_",
      "-Ydelambdafy:method",
      "-target:jvm-1.8"
    ) ++ crossScalacOptions(scalaVersion.value)),
  resolvers ++= Seq(
      Resolver.sonatypeRepo("snapshots"),
      Resolver.sonatypeRepo("releases"),
      "Seasar Repository" at "https://maven.seasar.org/maven2/"
    ),
  libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test
    ),
  Test / fork := true,
  Test / parallelExecution := false,
  ThisBuild / scalafmtOnCompile := true
)

val `docker-controller-scala-core` = (project in file("docker-controller-scala-core"))
  .settings(baseSettings, deploySettings)
  .settings(
    name := "docker-controller-scala-core",
    libraryDependencies ++= Seq(
        "org.slf4j"              % "slf4j-api"                         % "1.7.30",
        "com.github.docker-java" % "docker-java"                       % dockerJavaVersion,
        "com.github.docker-java" % "docker-java-transport-jersey"      % dockerJavaVersion,
        "com.github.docker-java" % "docker-java-transport-httpclient5" % dockerJavaVersion,
        "com.github.docker-java" % "docker-java-transport-okhttp"      % dockerJavaVersion,
        "me.tongfei"             % "progressbar"                       % "0.9.1",
        "org.seasar.util"        % "s2util"                            % "0.0.1",
        "org.freemarker"         % "freemarker"                        % "2.3.31",
        "ch.qos.logback"         % "logback-classic"                   % logbackVersion % Test,
        "commons-io"             % "commons-io"                        % "2.8.0" % Test,
        "org.scalatest"          %% "scalatest"                        % scalaTestVersion % Test
      ),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2L, scalaMajor)) if scalaMajor == 13 =>
          Seq.empty
        case Some((2L, scalaMajor)) if scalaMajor == 12 =>
          Seq(
            "org.scala-lang.modules" %% "scala-collection-compat" % scalaCollectionCompatVersion
          )
      }
    }
  )

val `docker-controller-scala-scalatest` = (project in file("docker-controller-scala-scalatest"))
  .settings(baseSettings, deploySettings)
  .settings(
    name := "docker-controller-scala-scalatest",
    libraryDependencies ++= Seq(
        "org.scalatest"  %% "scalatest"      % scalaTestVersion,
        "ch.qos.logback" % "logback-classic" % logbackVersion % Test
      )
  ).dependsOn(`docker-controller-scala-core`)

val `docker-controller-scala-dynamodb-local` = (project in file("docker-controller-scala-dynamodb-local"))
  .settings(baseSettings, deploySettings)
  .settings(
    name := "docker-controller-scala-dynamodb-local",
    libraryDependencies ++= Seq(
        "org.scalatest"  %% "scalatest"            % scalaTestVersion % Test,
        "ch.qos.logback" % "logback-classic"       % logbackVersion   % Test,
        "com.amazonaws"  % "aws-java-sdk-dynamodb" % "1.11.994"       % Test
      )
  ).dependsOn(`docker-controller-scala-core`, `docker-controller-scala-scalatest` % Test)

val `docker-controller-scala-minio` = (project in file("docker-controller-scala-minio"))
  .settings(baseSettings, deploySettings)
  .settings(
    name := "docker-controller-scala-minio",
    libraryDependencies ++= Seq(
        "org.scalatest"  %% "scalatest"      % scalaTestVersion % Test,
        "ch.qos.logback" % "logback-classic" % logbackVersion   % Test,
        "com.amazonaws"  % "aws-java-sdk-s3" % "1.11.994"       % Test
      )
  ).dependsOn(`docker-controller-scala-core`, `docker-controller-scala-scalatest` % Test)

val `docker-controller-scala-zookeeper` = (project in file("docker-controller-scala-zookeeper"))
  .settings(baseSettings, deploySettings)
  .settings(
    name := "docker-controller-scala-zookeeper",
    libraryDependencies ++= Seq(
        "org.scalatest"        %% "scalatest"      % scalaTestVersion % Test,
        "ch.qos.logback"       % "logback-classic" % logbackVersion   % Test,
        "org.apache.zookeeper" % "zookeeper"       % "3.7.0"          % Test
      )
  ).dependsOn(`docker-controller-scala-core`, `docker-controller-scala-scalatest` % Test)

val `docker-controller-scala-kafka` = (project in file("docker-controller-scala-kafka"))
  .settings(baseSettings, deploySettings)
  .settings(
    name := "docker-controller-scala-kafka",
    libraryDependencies ++= Seq(
        "org.scalatest"    %% "scalatest"      % scalaTestVersion % Test,
        "ch.qos.logback"   % "logback-classic" % logbackVersion   % Test,
        "org.apache.kafka" % "kafka-clients"   % "2.6.1"          % Test
      )
  ).dependsOn(
    `docker-controller-scala-core`,
    `docker-controller-scala-zookeeper`,
    `docker-controller-scala-scalatest` % Test
  )

val `docker-controller-scala-mysql` = (project in file("docker-controller-scala-mysql"))
  .settings(baseSettings, deploySettings)
  .settings(
    name := "docker-controller-scala-mysql",
    libraryDependencies ++= Seq(
        "org.scalatest"  %% "scalatest"           % scalaTestVersion % Test,
        "ch.qos.logback" % "logback-classic"      % logbackVersion   % Test,
        "mysql"          % "mysql-connector-java" % "8.0.23"         % Test
      )
  ).dependsOn(`docker-controller-scala-core`, `docker-controller-scala-scalatest` % Test)

val `docker-controller-scala-root` = (project in file("."))
  .settings(baseSettings, deploySettings)
  .settings(name := "docker-controller-scala-root")
  .aggregate(
    `docker-controller-scala-core`,
    `docker-controller-scala-scalatest`,
    `docker-controller-scala-mysql`,
    `docker-controller-scala-dynamodb-local`,
    `docker-controller-scala-minio`,
    `docker-controller-scala-zookeeper`,
    `docker-controller-scala-kafka`
  )
