import sbt.Keys.{libraryDependencies, scalacOptions}
import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings

import scala.sys.process._

lazy val oldVersion = "git describe --abbrev=0".!!.trim.replaceAll("^v", "")

def mimaSettings = mimaDefaultSettings ++ Seq(
  mimaCheckDirection := {
    def isPatch = {
      val Array(newMajor, newMinor, _) = version.value.split('.')
      val Array(oldMajor, oldMinor, _) = oldVersion.split('.')
      newMajor == oldMajor && newMinor == oldMinor
    }

    if (isPatch) "both" else "backward"
  },
  mimaPreviousArtifacts := {
    def isCheckingRequired = {
      val Array(newMajor, newMinor, _) = version.value.split('.')
      val Array(oldMajor, oldMinor, _) = oldVersion.split('.')
      newMajor == oldMajor && (newMajor != "0" || newMinor == oldMinor)
    }

    if (isCheckingRequired) Set(organization.value %% moduleName.value % oldVersion)
    else Set()
  }
)

lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  scalaVersion := "2.13.0",
  crossScalaVersions := Seq("2.13.0", "2.12.8"),
  releaseCrossBuild := true,
  startYear := Some(2016),
  organizationName := "Evolution Gaming",
  organizationHomepage := Some(url("https://www.evolutiongaming.com/")),
  bintrayOrganization := Some("evolutiongaming"),
  resolvers += Resolver.bintrayRepo("evolutiongaming", "maven"),
  homepage := Some(url("https://github.com/evolution-gaming/kryo-macros")),
  licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))),
  scalacOptions ++= Seq(
    "-encoding", "UTF-8",
    "-target:jvm-1.8",
    "-feature",
    "-unchecked",
    "-deprecation",
    "-Xlint",
    "-Ywarn-dead-code",
    "-Xmacro-settings:print-serializers"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, x)) if x >= 12 => Seq("-opt:l:method")
    case _ => Seq()
  })
)

lazy val kryo = project.in(file("."))
  .settings(commonSettings: _*)
  .settings(
    publish := ((): Unit),
  ).aggregate(macros, benchmark)

lazy val macros = project
  .settings(commonSettings: _*)
  .settings(mimaSettings: _*)
  .settings(
    name := "kryo-macros",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.esotericsoftware" % "kryo" % "4.0.2",
      "joda-time" % "joda-time" % "2.10.4",
      "org.joda" % "joda-convert" % "2.2.1",
      "org.scalatest" %% "scalatest" % "3.2.7" % Test
    )
  )

lazy val benchmark = project
  .dependsOn(macros)
  .enablePlugins(JmhPlugin)
  .settings(commonSettings: _*)
  .settings(
    name := "kryo-benchmark",
    publish := ((): Unit),
    libraryDependencies ++= Seq(
      "pl.project13.scala" % "sbt-jmh-extras" % "0.3.7",
      "org.scalatest" %% "scalatest" % "3.2.7" % Test
    )
  )
