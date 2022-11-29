// https://github.com/sbt/sbt/issues/6997
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

addSbtPlugin("com.github.sbt"    % "sbt-ci-release"         % "1.5.10")
addSbtPlugin("io.chrisdavenport" % "sbt-mima-version-check" % "0.1.2")
addSbtPlugin("io.chrisdavenport" % "sbt-no-publish"         % "0.1.0")
addSbtPlugin("com.47deg"         % "sbt-microsites"         % "1.3.4")
addSbtPlugin("org.scalameta"     % "sbt-mdoc"               % "2.3.6")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"           % "2.4.6")
addSbtPlugin("de.heikoseeberger" % "sbt-header"             % "5.8.0")
