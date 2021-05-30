name := "geo_service_server"

version := "0.1"

scalaVersion := "2.13.5"

libraryDependencies ++= Seq(
  "io.grpc" % "grpc-netty" % "1.4.0",
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
)

Compile / PB.targets := Seq(
  scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
)

// (optional) If you need scalapb/scalapb.proto or anything from
// google/protobuf/*.proto
libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
)

libraryDependencies += "com.lihaoyi" %% "upickle" % "0.9.5"
libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.7"
libraryDependencies += "io.etcd" % "jetcd-core" % "0.5.4"
libraryDependencies += "com.github.cb372" %% "scalacache-memcached" % "0.28.0"

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

mainClass in Compile := Some("service.Server")
packageName in Docker := "geo-service-server"

resolvers ++= Seq(
  "jitpack" at "https://jitpack.io/"
)
