name := "Project3"

version := "1.0"

scalaVersion := "2.11.7"

resolvers ++= Seq("RoundEights" at "http://maven.spikemark.net/roundeights")

libraryDependencies += "com.typesafe.akka" % "akka-actor_2.11" % "2.3.4"
libraryDependencies += "com.typesafe.akka" % "akka-remote_2.11" % "2.3.4"
libraryDependencies ++= Seq(
  "org.mindrot" % "jbcrypt" % "0.3m" % "optional",
  "org.specs2" %% "specs2" % "2.3.+" % "test"
)