val scala212Version  = "2.12.13"
val scala213Version  = "2.13.5"
val scalaTestVersion = "3.2.6"
val logbackVersion   = "1.2.3"

def crossScalacOptions(scalaVersion: String): Seq[String] = CrossVersion.partialVersion(scalaVersion) match {
  case Some((2L, scalaMajor)) if scalaMajor >= 12 =>
    Seq.empty
  case Some((2L, scalaMajor)) if scalaMajor <= 11 =>
    Seq("-Yinline-warnings")
}

lazy val deploySettings = Seq(
  sonatypeProfileName := "com.github.j5ik2o",
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := {
    <url>https://github.com/j5ik2o/docker-controller-scala</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
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
    val ivyCredentials = (baseDirectory in LocalRootProject).value / ".credentials"
    val gpgCredentials = (baseDirectory in LocalRootProject).value / ".gpgCredentials"
    Credentials(ivyCredentials) :: Credentials(gpgCredentials) :: Nil
  }
)

lazy val baseSettings = Seq(
  organization := "com.github.j5ik2o",
  scalaVersion := scala213Version,
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
      Resolver.sonatypeRepo("releases")
    ),
  libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalaTestVersion % Test
    ),
  Test / fork := true,
  parallelExecution in Test := false,
  scalafmtOnCompile in ThisBuild := true
)

val `docker-controller-scala-core` = (project in file("docker-controller-scala-core"))
  .settings(baseSettings, deploySettings)
  .settings(
    name := "testcontainers-for-scala-core",
    libraryDependencies ++= Seq(
        "com.github.docker-java" % "docker-java"                       % "3.2.7",
        "com.github.docker-java" % "docker-java-transport-httpclient5" % "3.2.7" % Provided,
        "org.slf4j"              % "slf4j-api"                         % "1.7.30",
        "ch.qos.logback"         % "logback-classic"                   % logbackVersion % Test,
        "org.scalaj"             %% "scalaj-http"                      % "2.4.2" % Test
      )
  )

val `docker-controller-scala-root` = (project in file("."))
  .settings(baseSettings, deploySettings)
  .settings(name := "docker-controller-scala-root")
  .aggregate(`docker-controller-scala-core`)
