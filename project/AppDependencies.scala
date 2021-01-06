import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-frontend-play-27" % "3.2.0",
    "uk.gov.hmrc" %% "play-frontend-hmrc"         % "0.34.0-play-27",
    "uk.gov.hmrc" %% "play-frontend-govuk"        % "0.56.0-play-27"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-27" % "3.2.0" % Test,
    "com.typesafe.play"      %% "play-test"              % current % Test,
    "org.scalatestplus.play" %% "scalatestplus-play"     % "4.0.3" % "test, it",
    "org.jsoup"              % "jsoup"                   % "1.13.1" % Test,
    "org.mockito"            % "mockito-core"            % "3.7.0",
    "com.vladsch.flexmark"   % "flexmark-all"            % "0.36.8" % "test, it",
    "org.pegdown"            % "pegdown"                 % "1.6.0" % "test, it"
  )
}
