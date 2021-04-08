val scala212Version              = "2.12.13"
val scala213Version              = "2.13.5"
val scalaTestVersion             = "3.2.6"
val logbackVersion               = "1.2.3"
val scalaCollectionCompatVersion = "2.4.3"

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
        "com.github.docker-java" % "docker-java"                       % "3.2.7",
        "org.slf4j"              % "slf4j-api"                         % "1.7.30",
        "org.seasar.util"        % "s2util"                            % "0.0.1",
        "org.freemarker"         % "freemarker"                        % "2.3.31",
        "ch.qos.logback"         % "logback-classic"                   % logbackVersion % Test,
        "com.github.docker-java" % "docker-java-transport-jersey"      % "3.2.7" % Test,
        "com.github.docker-java" % "docker-java-transport-httpclient5" % "3.2.7" % Test,
        "com.github.docker-java" % "docker-java-transport-okhttp"      % "3.2.7" % Test,
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

val `docker-controller-scala-dynamodb-local` = (project in file("docker-controller-scala-dynamodb-local"))
  .settings(baseSettings, deploySettings)
  .settings(
    name := "docker-controller-scala-dynamodb-local",
    libraryDependencies ++= Seq(
        "ch.qos.logback"         % "logback-classic"                   % logbackVersion   % Test,
        "com.github.docker-java" % "docker-java-transport-httpclient5" % "3.2.7"          % Test,
        "org.scalatest"          %% "scalatest"                        % scalaTestVersion % Test
      )
  ).dependsOn(`docker-controller-scala-core`)

val `docker-controller-scala-scalatest` = (project in file("docker-controller-scala-scalatest"))
  .settings(baseSettings, deploySettings)
  .settings(
    name := "docker-controller-scala-scalatest",
    libraryDependencies ++= Seq(
        "org.scalatest"          %% "scalatest"                        % scalaTestVersion,
        "com.github.docker-java" % "docker-java-transport-jersey"      % "3.2.7" % Provided,
        "com.github.docker-java" % "docker-java-transport-httpclient5" % "3.2.7" % Provided,
        "com.github.docker-java" % "docker-java-transport-okhttp"      % "3.2.7" % Provided,
        "commons-io"             % "commons-io"                        % "2.8.0" % Provided,
        "ch.qos.logback"         % "logback-classic"                   % logbackVersion % Test
      )
  ).dependsOn(`docker-controller-scala-core`)

val `docker-controller-scala-root` = (project in file("."))
  .settings(baseSettings, deploySettings)
  .settings(name := "docker-controller-scala-root")
  .aggregate(`docker-controller-scala-core`, `docker-controller-scala-scalatest`)
