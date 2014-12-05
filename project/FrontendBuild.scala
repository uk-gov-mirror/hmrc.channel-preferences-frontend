import play.PlayImport._
import play.PlayImport.PlayKeys._
import sbt._
import scala._
import scala.util.Properties._

object FrontendBuild extends Build with MicroService {

  import Dependencies._

  val appName = "preferences-frontend"
  val appVersion = envOrElse("PREFERENCES_FRONTEND_VERSION", "999-SNAPSHOT")

  override lazy val appDependencies = requiredDependencies

  override lazy val playSettings = Seq(routesImport ++= Seq(
    "controllers.sa.prefs._",
    "uk.gov.hmrc.domain._"
  ))
}

private object Dependencies {

  private val metricsGraphiteVersion = "3.0.1"
  private val playMetricsVersion = "0.1.3"
  private val scalatestVersion = "2.2.0"
  private val mockitoVersion = "1.9.5"
  private val pegdownVersion = "1.4.2"
  private val jsoupVersion = "1.7.2"


  val requiredDependencies = Seq(
    ws,
//    "com.kenshoo" %% "metrics-play" % playMetricsVersion,
//    "com.codahale.metrics" % "metrics-graphite" % metricsGraphiteVersion,
    "uk.gov.hmrc"    %% "govuk-template" % "2.1.0",
    "uk.gov.hmrc"    %% "play-frontend"  % "9.0.0",
    "uk.gov.hmrc"    %% "play-health"    % "0.5.0",
    "uk.gov.hmrc"    %% "emailaddress"   % "0.2.0",
    "uk.gov.hmrc"    %% "url-builder"    % "0.2.0",
    "uk.gov.hmrc"    %% "crypto"         % "1.3.0",
    "uk.gov.hmrc"    %% "play-ui"        % "1.1.0",
    "com.netaporter" %% "scala-uri"      % "0.4.2",

    "org.jsoup"      %  "jsoup"       % jsoupVersion  % "test",
    "org.scalatest"  %% "scalatest"   % scalatestVersion  % "test",
    "org.pegdown"    %  "pegdown"     % pegdownVersion  % "test",
    "org.mockito"    %  "mockito-all" % mockitoVersion  % "test",

    "org.scalatest" %% "scalatest" % scalatestVersion % "it",
    "org.pegdown" % "pegdown" % pegdownVersion % "it",
    "org.jsoup" % "jsoup" % jsoupVersion % "it"
  )
}
