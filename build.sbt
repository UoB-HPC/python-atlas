import org.scalajs.linker.interface.{ESFeatures, ESVersion}

import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

Global / onChangedBuildSource := ReloadOnSourceChanges

/** Custom task to start demo with webpack-dev-server, use as `<project>/start`. Just `start` also works, and starts all
  * frontend demos
  *
  * After that, the incantation is this to watch and compile on change: `~<project>/fastOptJS::webpack`
  */
lazy val start = TaskKey[Unit]("start")

/** Say just `dist` or `<project>/dist` to make a production bundle in `docs` for github publishing
  */
lazy val dist = TaskKey[File]("dist")

lazy val scala3Version = "3.1.3"
lazy val catsVersion   = "2.8.0"
lazy val munitVersion  = "1.0.0-M5"

lazy val commonSettings = Seq(
  scalaVersion     := scala3Version,
  version          := "0.0.1-SNAPSHOT",
  organization     := "uk.ac.bristol.uob-hpc",
  organizationName := "University of Bristol",
  scalacOptions ~= filterConsoleScalacOptions,
  javacOptions ++=
    Seq(
      "-parameters",
      "-Xlint:all"
    ) ++
      Seq("-source", "1.8") ++
      Seq("-target", "1.8"),
  scalacOptions ++= Seq(
//    "-explain",                              //
    "-no-indent",                            //
    "-Wconf:cat=unchecked:error",            //
    "-Wconf:name=MatchCaseUnreachable:error" //
    // "-Wconf:name=PatternMatchExhaustivity:error" // TODO enable later
    // "-language:strictEquality"
  ),
  scalafmtDetailedError := true,
  scalafmtFailOnErrors  := true
)

lazy val model = crossProject(JSPlatform, JVMPlatform)
  .settings(
    commonSettings,
    name := "model"
  )

lazy val generator = project
  .settings(
    commonSettings,
    name := "generator",
    libraryDependencies ++=
      Seq(
        "io.github.pityka" %% "nspl-awt" % "0.9.0",
//        "io.github.pityka"              %% "nspl-saddle"                % "0.9.0",
        "org.jgrapht"                    % "jgrapht-core"               % "1.5.1",
        "org.jgrapht"                    % "jgrapht-io"                 % "1.5.1",
        "org.scala-lang.modules"        %% "scala-parallel-collections" % "1.0.4",
        "org.typelevel"                 %% "cats-core"                  % catsVersion,
        "org.typelevel"                 %% "cats-parse"                 % "0.3.7",
        "com.lihaoyi"                   %% "upickle"                    % "2.0.0",
        "com.softwaremill.sttp.client3" %% "core"                       % "3.6.2",
        "org.scalameta"                 %% "munit"                      % "0.7.29" % Test
      )
  )
  .dependsOn(model.jvm)

lazy val webapp = project
  .enablePlugins(ScalaJSPlugin, ScalablyTypedConverterPlugin)
  .settings(
    commonSettings,
    name                            := "webapp",
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= ( //
      _.withSourceMap(false)
        .withModuleKind(ModuleKind.CommonJSModule)
        .withESFeatures(ESFeatures.Defaults.withESVersion(ESVersion.ES2015))
        .withParallel(true)
    ),
    useYarn              := true,
    webpackDevServerPort := 8001,
    stUseScalaJsDom      := true,
//    webpack / version := "4.46.0",
//    startWebpackDevServer / version   := "3.1.4",

    webpack / version               := "5.64.2",
    webpackCliVersion               := "4.10.0",
    startWebpackDevServer / version := "4.5.0",
    Compile / fastOptJS / webpackExtraArgs += "--mode=development",
    Compile / fullOptJS / webpackExtraArgs += "--mode=production",
    Compile / fastOptJS / webpackDevServerExtraArgs += "--mode=development",
    Compile / fullOptJS / webpackDevServerExtraArgs += "--mode=production",
    webpackConfigFile := Some((ThisBuild / baseDirectory).value / "custom.webpack.config.js"),
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.2.0",
      "com.raquo"    %%% "laminar"     % "0.14.2",
      "com.raquo"    %%% "waypoint"    % "0.5.0"
    ),
    stIgnore ++= List(
      "csstype",
      "react",
      "prop-types",
      "hammerjs",
      "node",
      "@math.gl/core",
      "@luma.gl/webgl",
      "@luma.gl/core",
      "@deck.gl/layers",
      "@deck.gl/core",
      "@deck.gl/react",
      "bulma",
      "@fortawesome/fontawesome-free",
      "hyperlist"
    ),
    Compile / npmDependencies ++= Seq(
      // CSS and layout
      "@fortawesome/fontawesome-free" -> "5.15.4",
      "bulma"                         -> "0.9.4",

      // Visualisations
      "@deck.gl/core"   -> "8.8.3",
      "@deck.gl/layers" -> "8.8.3",

      // Fuzzy search
      "quick-score" -> "0.2.0",
      "hyperlist"   -> "1.0.0"
    ),
    Compile / npmDevDependencies ++= Seq(
      "webpack-merge" -> "4.1",
      "css-loader"    -> "2.1.0",
      "style-loader"  -> "0.23.1",
      "file-loader"   -> "3.0.1",
      "url-loader"    -> "1.1.2",
      "ignore-loader" -> "0.1.2"
    )
  )
  .settings(
    start := {
      (Compile / fastOptJS / startWebpackDevServer).value
    },
    dist := {
      val artifacts      = (Compile / fullOptJS / webpack).value
      val artifactFolder = (Compile / fullOptJS / crossTarget).value
      val distFolder     = (ThisBuild / baseDirectory).value / "docs"

      distFolder.mkdirs()
      artifacts.foreach { artifact =>
        val target = artifact.data.relativeTo(artifactFolder) match {
          case None          => distFolder / artifact.data.name
          case Some(relFile) => distFolder / relFile.toString
        }

        Files.copy(artifact.data.toPath, target.toPath, REPLACE_EXISTING)
      }

      val index           = "index.html"
      val publicResources = baseDirectory.value / "src/main/js/public/"

      Files.list(publicResources.toPath).filter(_.getFileName.toString != index).forEach { p =>
        Files.copy(p, (distFolder / p.getFileName.toString).toPath, REPLACE_EXISTING)
      }

      val indexFrom = publicResources / index
      val indexTo   = distFolder / index

      val indexPatchedContent = {
        import collection.JavaConverters._
        Files
          .readAllLines(indexFrom.toPath, IO.utf8)
          .asScala
          .map(_.replaceAllLiterally("-fastopt-", "-opt-"))
          .mkString("\n")
      }

      Files.write(indexTo.toPath, indexPatchedContent.getBytes(IO.utf8))
      distFolder
    }
  )
  .dependsOn(model.js)

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .aggregate(model.jvm, generator, model.js, webapp)
