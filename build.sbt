import org.typelevel.sbt.gha.WorkflowStep._

val azureCosmosV = "4.39.0"
val azureDocumentDBV = "2.6.5"
val catsV = "2.9.0"
val catsEffectV = "3.5.0"
val circeJackson210V = "0.14.0"
val documentDBBulkExecV = "2.12.5"
val fs2V = "3.7.0"
val circeV = "0.14.5"
val munitV = "0.7.29"
val munitCatsEffectV = "1.0.7"
val kindProjectorV = "0.13.2"

ThisBuild / tlBaseVersion := "1.0"
ThisBuild / organization := "com.banno"
ThisBuild / organizationName := "Jack Henry & Associates, Inc.®"
ThisBuild / startYear := Some(2020)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  Developer(
    "ChristopherDavenport",
    "Christopher Davenport",
    "chris@christopherdavenport.tech",
    url("https://github.com/ChristopherDavenport")
  ),
  Developer(
    "JesusMtnez",
    "Jesús Martínez",
    "jesusmartinez93@gmail.com",
    url("https://github.com/JesusMtnez")
  ),
  Developer(
    "BeniVF",
    "Benigno Villa Fernández",
    "beni.villa@gmail.com",
    url("https://github.com/BeniVF")
  ),
  Developer(
    "Ryan-Banno",
    "Ryan D",
    "ryan.delap@banno.com",
    url("https://github.com/Ryan-Banno")
  )
)

ThisBuild / tlSonatypeUseLegacyHost := true //https://oss.sonatype.org/ currently

val scala3 = "3.3.0"
ThisBuild / crossScalaVersions := Seq(scala3, "2.13.11")
ThisBuild / scalaVersion := scala3

lazy val `cosmos4s` = project
  .in(file("."))
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .aggregate(core)

lazy val core = project
  .in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "cosmos4s"
  )

// General Settings
lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "com.azure"           % "azure-cosmos"            % azureCosmosV,
    "com.microsoft.azure" % "azure-documentdb"        % azureDocumentDBV,
    "com.microsoft.azure" % "documentdb-bulkexecutor" % documentDBBulkExecV,
    "org.typelevel"      %% "cats-core"               % catsV,
    "org.typelevel"      %% "cats-effect"             % catsEffectV,
    "co.fs2"             %% "fs2-reactive-streams"    % fs2V,
    "io.circe"           %% "circe-core"              % circeV,
    "io.circe"           %% "circe-parser"            % circeV,
    "io.circe"           %% "circe-jackson210"        % circeJackson210V,
    "org.scalameta"      %% "munit"                   % munitV           % Test,
    "org.typelevel"      %% "munit-cats-effect-3"     % munitCatsEffectV % Test
  ) ++
  // format: off
  (if (scalaVersion.value.startsWith("2"))
    Seq(compilerPlugin(("org.typelevel" %% "kind-projector" % kindProjectorV).cross(CrossVersion.full)))
  else Seq()),
)

Compile / scalacOptions ++= Seq(
  "-groups",
  "-sourcepath",
  (LocalRootProject / baseDirectory).value.getAbsolutePath
)
import laika.helium.config._
import laika.ast.Image
ThisBuild / tlSitePublishBranch := Some("main")
lazy val docs = project.in(file("site")).enablePlugins(TypelevelSitePlugin)

lazy val unidocs = project
  .in(file("unidocs"))
  .enablePlugins(TypelevelUnidocPlugin) // also enables the ScalaUnidocPlugin
  .settings(
    name := "cosmos4s-docs",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(core)
  )

inThisBuild(
  List(
    // This is nasty and can go away after
    // https://github.com/typelevel/sbt-typelevel/issues/442
    tlCiDependencyGraphJob := false,
    githubWorkflowAddedJobs += WorkflowJob(
      "dependency-submission",
      "Submit Dependencies",
      scalas = List(scalaVersion.value),
      javas = List(githubWorkflowJavaVersions.value.head),
      steps = githubWorkflowJobSetup.value.toList :+
        Use(
          UseRef.Public("scalacenter", "sbt-dependency-submission", "v2"),
          name = Some("Submit Dependencies"),
          params = Map(
            "modules-ignore" -> "docs_2.12 docs_2.13 docs_3",
            "configs-ignore" -> "scala-doc-tool scala-tool test"
          )
        )
    ).copy(cond = Some("github.event_name != 'pull_request'"))
  )
)
