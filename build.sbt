name := "kryo-macros"

organization := "com.evolutiongaming"

homepage := Some(new URL("http://github.com/evolution-gaming/kryo-macros"))

startYear := Some(2016)

organizationName := "Evolution Gaming"

organizationHomepage := Some(url("http://evolutiongaming.com"))

bintrayOrganization := Some("evolutiongaming")

scalaVersion := "2.11.8"

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Xfuture"
)

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "com.esotericsoftware.kryo" % "kryo" % "2.21",
  "joda-time" % "joda-time" % "2.8",
  "org.joda" % "joda-convert" % "1.7",
  "org.scalatest" %% "scalatest" % "3.0.0" % Test
)

licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")))
