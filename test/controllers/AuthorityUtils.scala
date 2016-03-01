package controllers

import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.frontend.auth.connectors.domain._

object AuthorityUtils {

  def saAuthority(id: String, utr: String): Authority =
    Authority(s"/auth/oid/$id", Accounts(sa = Some(SaAccount(s"/sa/individual/$utr", SaUtr(utr)))), None, None, credentialStrength = CredentialStrength.Strong, confidenceLevel = ConfidenceLevel.L50)

  def emptyAuthority(id: String): Authority =
    Authority(s"/auth/oid/$id", Accounts(), None, None, credentialStrength = CredentialStrength.Strong, confidenceLevel = ConfidenceLevel.L50)

}