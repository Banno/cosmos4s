val catsV = "2.6.0"
val catsEffectV = "2.5.0"
val fs2V = "2.5.5"
val circeV = "0.13.0"
val specs2V = "4.10.6"
// compiler plugins
val kindProjectorV = "0.11.3"
val betterMonadicForV = "0.3.1"

lazy val `cosmos4s` = project
  .in(file("."))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .aggregate(core)

lazy val core = project
  .in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "cosmos4s"
  )

lazy val site = project
  .in(file("site"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(MdocPlugin)
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .dependsOn(core)
  .settings {
    import microsites._
    Seq(
      micrositeName := "cosmos4s",
      micrositeDescription := "Cosmos Access Api",
      micrositeAuthor := "Banno",
      micrositeGithubOwner := "Banno",
      micrositeGithubRepo := "cosmos4s",
      micrositeBaseUrl := "/cosmos4s",
      micrositeDocumentationUrl := "https://www.javadoc.io/doc/com.banno/cosmos4s_2.12",
      micrositeFooterText := None,
      micrositeHighlightTheme := "atom-one-light",
      micrositePalette := Map(
        "brand-primary" -> "#3e5b95",
        "brand-secondary" -> "#294066",
        "brand-tertiary" -> "#2d5799",
        "gray-dark" -> "#49494B",
        "gray" -> "#7B7B7E",
        "gray-light" -> "#E5E5E6",
        "gray-lighter" -> "#F4F3F4",
        "white-color" -> "#FFFFFF"
      ),
      micrositeCompilingDocsTool := WithMdoc,
      Tut / scalacOptions --= Seq(
        "-Xfatal-warnings",
        "-Ywarn-unused-import",
        "-Ywarn-numeric-widen",
        "-Ywarn-dead-code",
        "-Ywarn-unused:imports",
        "-Xlint:-missing-interpolator,_"
      ),
      micrositePushSiteWith := GitHub4s,
      micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
      micrositeExtraMdFiles := Map(
        file("CODE_OF_CONDUCT.md") -> ExtraMdFileConfig(
          "code-of-conduct.md",
          "page",
          Map("title" -> "code of conduct", "section" -> "code of conduct", "position" -> "100")
        ),
        file("LICENSE") -> ExtraMdFileConfig(
          "license.md",
          "page",
          Map("title" -> "license", "section" -> "license", "position" -> "101")
        )
      )
    )
  }

// General Settings
lazy val commonSettings = Seq(
  crossScalaVersions := Seq(scalaVersion.value, "2.12.13"),
  addCompilerPlugin("org.typelevel" %% "kind-projector"     % kindProjectorV cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % betterMonadicForV),
  libraryDependencies ++= Seq(
    "com.azure"           % "azure-cosmos"            % "4.14.0",
    "com.microsoft.azure" % "azure-documentdb"        % "2.6.1",
    "com.microsoft.azure" % "documentdb-bulkexecutor" % "2.12.0",
    "org.typelevel"      %% "cats-core"               % catsV,
    "org.typelevel"      %% "cats-effect"             % catsEffectV,
    "co.fs2"             %% "fs2-reactive-streams"    % fs2V,
    "io.circe"           %% "circe-core"              % circeV,
    "io.circe"           %% "circe-parser"            % circeV,
    "io.circe"           %% "circe-jackson210"        % "0.13.0",
    "org.specs2"         %% "specs2-core"             % specs2V % Test,
    "org.specs2"         %% "specs2-scalacheck"       % specs2V % Test
  )
)

ThisBuild / organization := "com.banno"
Compile / scalacOptions ++= Seq(
  "-groups",
  "-sourcepath",
  (LocalRootProject / baseDirectory).value.getAbsolutePath,
  "-doc-source-url",
  "https://github.com/banno/cosmos4s/blob/v" + version.value + "€{FILE_PATH}.scala"
)

// General Settings
inThisBuild(
  List(
    scalaVersion := "2.13.5",
    developers := List(
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
    ),
    homepage := Some(url("https://github.com/Banno/cosmos4s")),
    organizationName := "Jack Henry & Associates, Inc.®",
    startYear := Some(2020),
    licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))
  )
)
