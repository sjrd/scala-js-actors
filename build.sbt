import ScalaJSKeys._

val commonSettings = Seq(
    organization := "org.scalajs",
    version := "0.1-SNAPSHOT",
    scalaVersion := "2.10.4",
    normalizedName ~= { _.replace("scala-js", "scalajs") },
    scalacOptions ++= Seq(
        "-deprecation",
        "-unchecked",
        "-feature",
        "-encoding", "utf8"
    )
)

lazy val root = project.in(file(".")).settings(commonSettings: _*)
  .aggregate(actors, akkaWebsocketBridge)

lazy val actors = project.settings(commonSettings: _*)
  .settings(
      unmanagedSourceDirectories in Compile +=
        (sourceDirectory in Compile).value / "wscommon"
  )

lazy val akkaWebsocketBridge = project.in(file("akka-websocket-bridge"))
  .settings(commonSettings: _*)
  .settings(
      unmanagedSourceDirectories in Compile +=
        (sourceDirectory in (actors, Compile)).value / "wscommon"
  )

lazy val examples = project.settings(commonSettings: _*)
  .aggregate(webworkersExample, faultToleranceExample,
      chatExample, chatExampleScalaJS)

lazy val webworkersExample = project.in(file("examples/webworkers"))
  .settings(commonSettings: _*)
  .dependsOn(actors)

lazy val faultToleranceExample = project.in(file("examples/faulttolerance"))
  .settings(commonSettings: _*)
  .dependsOn(actors)

lazy val chatExample = project.in(file("examples/chat-full-stack")).enablePlugins(PlayScala)
  .settings(commonSettings: _*)
  .dependsOn(akkaWebsocketBridge)
  .settings(
      unmanagedSourceDirectories in Compile +=
        baseDirectory.value / "cscommon"
  )

lazy val chatExampleScalaJS = project.in(file("examples/chat-full-stack/scalajs"))
  .settings((commonSettings ++ scalaJSSettings): _*)
  .dependsOn(actors)
  .settings(
      unmanagedSourceDirectories in Compile +=
        (baseDirectory in chatExample).value / "cscommon",
      fastOptJS in Compile <<= (fastOptJS in Compile) triggeredBy (compile in (chatExample, Compile))
  )
  .settings(
      Seq(fastOptJS, fullOptJS) map {
        packageJSKey =>
          crossTarget in (Compile, packageJSKey) :=
            (baseDirectory in chatExample).value / "public/javascripts"
      }: _*
  )
