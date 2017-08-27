// *****************************************************************************
// Projects
// *****************************************************************************

// Calculate the current year for usage in copyright notices and license headers.
lazy val currentYear: Int = java.time.OffsetDateTime.now().getYear

lazy val tenseiServer =
  project
    .in(file("."))
    .enablePlugins(AutomateHeaderPlugin, GitBranchPrompt, GitVersioning)
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
        library.akkaCluster,
        library.akkaClusterTools,
        library.akkaSlf4j,
        library.argonaut,
        library.cats,
        library.dfasdlCore,
        library.dfasdlUtils,
        library.logbackClassic,
        library.tenseiApi,
        library.akkaTestkit     % Test,
        library.scalaTest       % Test
      )
    )
    .enablePlugins(JavaServerAppPackaging, JDebPackaging, SystemVPlugin)
    .settings(
      daemonUser := "tensei-server",
      daemonGroup := "tensei-server",
      debianPackageProvides in Debian += "tensei-server",
      debianPackageDependencies in Debian += "openjdk-8-jre-headless",
      defaultLinuxInstallLocation := "/opt",
      maintainer := "Wegtam GmbH <devops@wegtam.com>",
      maintainerScripts in Debian := maintainerScriptsAppend((maintainerScripts in Debian).value)(
        DebianConstants.Postinst -> s"touch ${defaultLinuxInstallLocation.value}/${normalizedName.value}/tensei.license && chown ${daemonUser.value} ${defaultLinuxInstallLocation.value}/${normalizedName.value}/tensei.license"
      ),
      packageSummary := "Tensei-Data Server",
      packageDescription := "The tensei server is the heart of a Tensei-Data system.",
      // Disable packaging of api-docs.
      publishArtifact in (Compile, packageDoc) := false,
      publishArtifact in packageDoc := false,
      sources in (Compile, doc) := Seq.empty,
      // Package our configuration files into an extra directory.
      // FIXME There has to be a sane way to do this!
      mappings in Universal += new File(targetDirectory.value + "/logback.xml") -> "conf/logback.xml",
      mappings in Universal += new File(targetDirectory.value + "/application.conf") -> "conf/application.conf",
      mappings in Universal += new File(targetDirectory.value + "/tensei.conf") -> "conf/tensei.conf",
      mappings in Universal += baseDirectory.in(ThisBuild).value / "free.license" -> "free.license",
      // Require tests to be run before building a debian package.
      packageBin in Debian := ((packageBin in Debian) dependsOn (test in Test)).value
    )

lazy val targetDirectory = Def.setting(target.value + "/scala-" + scalaVersion.value.substring(0, scalaVersion.value.indexOf(".", 2)) + "/classes")

// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {
    object Version {
      val akka        = "2.4.17"
      val argonaut    = "6.0.4"
      val cats        = "0.9.0"
      val dfasdlCore  = "1.0"
      val dfasdlUtils = "1.0.0"
      val logback     = "1.1.11"
      val scalaTest   = "3.0.1"
      val tenseiApi   = "1.92.0"
    }
    val akkaCluster      = "com.typesafe.akka" %% "akka-cluster"       % Version.akka
    val akkaClusterTools = "com.typesafe.akka" %% "akka-cluster-tools" % Version.akka
    val akkaSlf4j        = "com.typesafe.akka" %% "akka-slf4j"         % Version.akka
    val akkaTestkit      = "com.typesafe.akka" %% "akka-testkit"       % Version.akka
    val argonaut         = "io.argonaut"       %% "argonaut"           % Version.argonaut
    val cats             = "org.typelevel"     %% "cats"               % Version.cats
    val dfasdlCore       = "org.dfasdl"        %% "dfasdl-core"        % Version.dfasdlCore
    val dfasdlUtils      = "org.dfasdl"        %% "dfasdl-utils"       % Version.dfasdlUtils
    val logbackClassic   = "ch.qos.logback"    %  "logback-classic"    % Version.logback
    val scalaTest        = "org.scalatest"     %% "scalatest"          % Version.scalaTest
    val tenseiApi        = "com.wegtam.tensei" %% "tensei-api"         % Version.tenseiApi
  }

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings =
  commonSettings ++
  resolverSettings ++
  scalafmtSettings

lazy val commonSettings =
  Seq(
    name := "tensei-server",
    headerLicense := Some(HeaderLicense.AGPLv3(s"2014 - $currentYear", "Contributors as noted in the AUTHORS.md file")),
    organization := "com.wegtam.tensei",
    git.useGitDescribe := true,
    scalaVersion in ThisBuild := "2.11.11",
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-target:jvm-1.8",
      "-unchecked",
      "-Xfatal-warnings",
      "-Xfuture",
      "-Xlint",
      "-Ydelambdafy:method",
      "-Ybackend:GenBCode",
      "-Yno-adapted-args",
      "-Ywarn-numeric-widen",
      "-Ywarn-unused-import",
      "-Ywarn-value-discard"
    ),
    javacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-source", "1.8",
      "-target", "1.8"
    ),
    incOptions := incOptions.value.withNameHashing(nameHashing = true)
    //wartremoverWarnings in (Compile, compile) ++= Warts.unsafe
  )

lazy val resolverSettings =
  Seq(
    resolvers += Resolver.bintrayRepo("wegtam", "dfasdl"),
    resolvers += Resolver.bintrayRepo("wegtam", "tensei-data")
  )

lazy val scalafmtSettings =
  Seq(
    scalafmtOnCompile := true,
    scalafmtOnCompile.in(Sbt) := false,
    scalafmtVersion := "1.2.0"
  )

