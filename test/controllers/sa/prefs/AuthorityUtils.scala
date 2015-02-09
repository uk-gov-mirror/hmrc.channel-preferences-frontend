package controllers.sa.prefs

import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.microservice.auth.domain.{Accounts, Authority, SaAccount}

object AuthorityUtils {

  def saAuthority(id: String, utr: String): Authority =
    Authority(s"/auth/oid/$id", Accounts(sa = Some(SaAccount(s"/sa/individual/$utr", SaUtr(utr)))), None, None)

  def emptyAuthority(id: String): Authority =
    Authority(s"/auth/oid/$id", Accounts(), None, None)

}
