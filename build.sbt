name := "kryo-macros"

organization := "com.evolutiongaming"

homepage := Some(new URL("http://github.com/evolution-gaming/kryo-macros"))

startYear := Some(2016)

organizationName := "Evolution Gaming"

organizationHomepage := Some(url("http://evolutiongaming.com"))

bintrayOrganization := Some("evolutiongaming")

scalaVersion := "2.12.2"

crossScalaVersions := Seq("2.12.2", "2.11.11")

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Xfuture"
)

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "com.esotericsoftware" % "kryo" % "4.0.0",
  "joda-time" % "joda-time" % "2.8",
  "org.joda" % "joda-convert" % "1.7",
  "org.scalatest" %% "scalatest" % "3.0.3" % Test
)

licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")))

releaseCrossBuild := true
