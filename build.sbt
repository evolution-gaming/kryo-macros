import sbt.Keys.scalacOptions

lazy val kryo = project.in(file("."))
  .settings(
    crossScalaVersions := Seq("2.12.3", "2.11.11"),
    releaseCrossBuild := true,
    publish := (),
    inThisBuild(Seq(
      organization := "com.evolutiongaming",
      scalaVersion := "2.12.3",
      startYear := Some(2016),
      organizationName := "Evolution Gaming",
      organizationHomepage := Some(url("https://www.evolutiongaming.com/")),
      bintrayOrganization := Some("evolutiongaming"),
      homepage := Some(url("https://github.com/evolution-gaming/kryo-macros")),
      licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))),
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
    ))
  ).aggregate(macros, benchmark)

lazy val macros = project
  .settings(
    name := "kryo-macros",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.esotericsoftware" % "kryo" % "4.0.0",
      "joda-time" % "joda-time" % "2.8",
      "org.joda" % "joda-convert" % "1.7",
      "org.scalatest" %% "scalatest" % "3.0.3" % Test
    )
  )

lazy val benchmark = project
  .enablePlugins(JmhPlugin)
  .settings(
    name := "kryo-benchmark",
    publish := (),
    libraryDependencies ++= Seq(
      "pl.project13.scala" % "sbt-jmh-extras" % "0.2.27",
      "org.scalatest" %% "scalatest" % "3.0.3" % Test
    )
  ).dependsOn(macros)
