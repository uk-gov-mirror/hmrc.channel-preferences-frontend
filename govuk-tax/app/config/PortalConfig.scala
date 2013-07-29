package config

import play.api.Play

object PortalConfig {

  import play.api.Play.current

  private lazy val env = Play.mode

  lazy val destinationRoot = s"${Play.configuration.getString(s"govuk-tax.$env.portal.destinationRoot").getOrElse("http://localhost:8080/portal/ssoin")}"
  lazy val ssoRoot = s"${Play.configuration.getString(s"govuk-tax.$env.portal.ssoRoot").getOrElse("http://localhost:8080")}"
}

