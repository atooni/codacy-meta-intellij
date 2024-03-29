ThisBuild / ideaBuild := "191.7479.19"

// Download the IDEA SDK on startup
onLoad in Global := ((s: State) => { "updateIdea" :: s}) compose (onLoad in Global).value

lazy val commonSettings = Seq(
  scalaSource       in Compile  := baseDirectory.value / "src",
  scalaSource       in Test     := baseDirectory.value / "test",
  javaSource        in Compile  := baseDirectory.value / "src",
  javaSource        in Test     := baseDirectory.value / "test",
  resourceDirectory in Compile  := baseDirectory.value / "resources",
  resourceDirectory in Test     := baseDirectory.value / "test-resources",

  scalaVersion := "2.12.8",

  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked"
  ),

  javacOptions ++= Seq(
    "-Xlint:deprecation"
  ),

  mainClass in (Compile, run) := Some("com.intellij.idea.Main"),

  fork in run := true,
  javaOptions in run := Seq(
    "-ea", // enable Java assertions
    s"-Didea.home.path=${ideaBaseDirectory.value}"
  )

)

name := "codacy-meta-intellij"
version := "0.1-SNAPSHOT"

lazy val root = (project in file(".")).
  aggregate(codacymetaIntellij)

lazy val codacymetaIntellij = (project in file("codacy-meta-intellij")).
  enablePlugins(SbtIdeaPlugin).
  settings(commonSettings).
  settings(
    name := "codacy-meta-intellij",
    ideaPluginName := "codacymetaIntellij",

    description := "Run Codacy Scalameta inspections on Scala code",

    ideaInternalPlugins := Seq(
      "IntelliLang",
    ),

    ideaExternalPlugins +=
      IdeaPlugin.Zip("Scala", url("https://plugins.jetbrains.com/files/1347/61993/scala-intellij-bin-2019.1.8.zip")),

    Compile/unmanagedClasspath ++=
      Option((baseDirectory.value / "lib").listFiles()).toList.flatMap(_.toList),

    packageFileMappings ++=
      Option((baseDirectory.value / "lib").listFiles()).toList.flatMap(_.toSeq)
      .map(f => (f, "lib/" + f.getName())),

    packageLibraryMappings ++= Seq(
      "org.scalameta" %% ".*" % "4.0.0" -> Some("lib/scalameta.jar")
    ),

    libraryDependencies ++= Seq(
      "com.codacy" %% "codacy-plugins-api" % "3.0.96",
      "com.codacy" %% "codacy-engine-scala-seed" % "3.0.168",
      "org.scalameta" %% "scalameta" % "4.0.0"
    ),

    ideaFullJars := {
      ideaFullJars.value.filter(af => !af.data.getName().startsWith("scalameta"))
    }
  )
