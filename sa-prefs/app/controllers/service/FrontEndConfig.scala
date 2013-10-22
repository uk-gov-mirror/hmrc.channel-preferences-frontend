package controllers.service

import scala.collection.JavaConversions._
import java.util.Collections
import play.api.Play

object FrontEndConfig {

  import play.api.Play.current
  lazy val env = Play.mode

  lazy val redirectDomainWhiteList = Play.configuration.getStringList(s"sa-prefs.$env.portal.redirectDomainWhiteList").getOrElse(Collections.emptyList()).toSet
  lazy val tokenTimeout = Play.configuration.getInt(s"sa-prefs.$env.portal.tokenTimeout").getOrElse(240)
  lazy val portalHome = Play.configuration.getString(s"sa-prefs.$env.portal.destinationRoot").getOrElse("http://hmrc.gov.uk") + Play.configuration.getString(s"sa-prefs.$env.portal.destinationPath.home").getOrElse("")
}