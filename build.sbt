name := """bol-game"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava, PlayEbean)

scalaVersion := "2.11.7"

val akkaVersion = "2.4.0"

playEbeanModels in Compile := Seq("model.*")

libraryDependencies ++= Seq(
  evolutions,
  javaJdbc,
  cache,
  javaWs,
  "junit" % "junit" % "4.12" % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.google.guava" %  "guava" % "18.0",
  "com.rabbitmq" % "amqp-client" % "3.4.4",
  "org.hamcrest" % "hamcrest-all" % "1.3" % "test"
)

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

