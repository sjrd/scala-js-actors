import ScalaJSKeys._

scalaJSSettings

scalaVersion := "2.10.3"

name := "Scala.js actors"

scalaJSTestFramework in Test := "org.scalajs.actors.test.ActorsTestFramework"

scalaJSTestBridgeClass in Test := "org.scalajs.actors.test.ActorsTestBridge"
