package controllers.common

import play.api.mvc.{Session, AnyContent, Request, SimpleResult}
import uk.gov.hmrc.common.microservice.domain.{User, RegimeRoots}
import scala.Some
import play.api.Logger
import uk.gov.hmrc.common.microservice.audit.AuditConnector
import uk.gov.hmrc.common.microservice.auth.AuthConnector
import controllers.common.service.Connectors
import controllers.common.actions.Actions
import scala.concurrent.Future

class HomeController(override val auditConnector: AuditConnector)
                    (implicit override val authConnector: AuthConnector)
  extends BaseController
  with Actions
  with AllRegimeRoots {

  def this() = this(Connectors.auditConnector)(Connectors.authConnector)

  def landing = UnauthorisedAction {
    request => redirectToLoginPage
  }

  private[common] def redirectToLoginPage: SimpleResult = {
    Redirect(routes.LoginController.login()).withNewSession
  }

  def home = AuthenticatedBy(AnyLoggedInUser) {
    user => implicit request => redirectToHomepage(user, session)
  }

  private[common] def redirectToHomepage(user: User, session: Session): SimpleResult = {
    user.regimes match {
      case RegimeRoots(Some(paye), _, _, _, _) => FrontEndRedirect.forSession(session) // TODO: Should this really be doing "forSession" here? or just going 'toPaye'?
      case RegimeRoots(_, Some(sa), _, _, _) => FrontEndRedirect.toBusinessTax
      case RegimeRoots(_, _, Some(vat), _, _) => FrontEndRedirect.toBusinessTax
      case RegimeRoots(_, _, _, Some(epaye), _) => FrontEndRedirect.toBusinessTax
      case RegimeRoots(_, _, _, _, Some(ct)) => FrontEndRedirect.toBusinessTax
      case RegimeRoots(None, None, None, None, None) if user.nameFromGovernmentGateway != None => FrontEndRedirect.toBusinessTax
      case _ =>
        Logger.info(s"User '${user.userId}' not authorised for any regime, regime roots: ${user.regimes}")
        redirectToLoginPage
    }
  }

  private object AnyLoggedInUser extends AuthenticationProvider with CookieEncryption {
    def handleNotAuthenticated(request: Request[AnyContent], redirectToOrigin: Boolean) = {
      case (None, token@_) =>
        Logger.info(s"No identity cookie found - redirecting to login. user: None token: $token")
        Future.successful(Right(redirectToLoginPage))
    }
  }
}
