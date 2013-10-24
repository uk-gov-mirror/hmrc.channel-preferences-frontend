package controllers.common

import play.api.mvc._
import controllers.common.service._
import uk.gov.hmrc.common.microservice.domain.{RegimeRoots, TaxRegime, User}
import uk.gov.hmrc.common.microservice.auth.domain.UserAuthority
import views.html.login
import com.google.common.net.HttpHeaders
import play.api.Logger
import controllers.common.actions.{LoggingActionWrapper, AuditActionWrapper, HeaderActionWrapper}
import controllers.common.FrontEndRedirect._

trait HeaderNames {
  val requestId = "X-Request-ID"
  val authorisation = HttpHeaders.AUTHORIZATION
  val forwardedFor = "x-forwarded-for"
  val xSessionId = "X-Session-ID"
}

object HeaderNames extends HeaderNames

@deprecated("please use Actions", "24.10.13")
trait ActionWrappers
  extends MicroServices
  with Results
  with CookieEncryption
  with HeaderActionWrapper
  with AuditActionWrapper
  with SessionTimeoutWrapper
  with LoggingActionWrapper {

  trait AuthorisedAction {
    def handleNotAuthorised(request: Request[AnyContent], redirectToOrigin: Boolean): PartialFunction[(Option[String], Option[String]), Result]

    def apply(taxRegime: Option[TaxRegime] = None, redirectToOrigin: Boolean = false)(action: (User => (Request[AnyContent] => SimpleResult))): Action[AnyContent] = {
      def handleAuthorised(request: Request[AnyContent]): PartialFunction[(Option[String], Option[String]), Result] = {
        case (Some(encryptedUserId), tokenOption) =>
          val userId = decrypt(encryptedUserId)
          val token = tokenOption.map(decrypt)
          val userAuthority = authMicroService.authority(userId)
          Logger.debug(s"Received user authority: $userAuthority")

          userAuthority match {
            case (Some(ua)) => {
              taxRegime match {
                case Some(regime) if !regime.isAuthorised(ua.regimes) =>
                  Logger.info("user not authorised for " + regime.getClass)
                  Redirect(regime.unauthorisedLandingPage)
                case _ =>
                  val user = User(
                    userId = userId,
                    userAuthority = ua,
                    regimes = getRegimeRootsObject(ua),
                    nameFromGovernmentGateway = decrypt(request.session.get("name")),
                    decryptedToken = token)

                  auditRequest(user, request)
                  action(user)(request)
              }
            }
            case _ => {
              Logger.warn(s"No authority found for user id '$userId' from '${request.remoteAddress}'")
              Unauthorized(login()).withNewSession
            }
          }
      }
      WithHeaders {
        WithRequestLogging {
          WithSessionTimeoutValidation {
            WithRequestAuditing {
              Action {
                request =>
                  val handle = handleNotAuthorised(request, redirectToOrigin) orElse handleAuthorised(request)
                  handle((request.session.get("userId"), request.session.get("token")))
              }
            }
          }
        }
      }
    }
  }

  object AuthorisedForIdaAction extends AuthorisedAction {
    def handleRedirect(request: Request[AnyContent], redirectToOrigin: Boolean): SimpleResult = {
      val redirectUrl = if (redirectToOrigin) Some(request.uri) else None
      toSamlLogin.withSession(buildSessionForRedirect(request.session, redirectUrl))
    }
    def handleNotAuthorised(request: Request[AnyContent], redirectToOrigin: Boolean): PartialFunction[(Option[String], Option[String]), Result] = {
      case (None, token @ _) =>
        Logger.info(s"No identity cookie found - redirecting to login. user: None token : ${token}")
        handleRedirect(request, redirectToOrigin)
      case (Some(encryptedUserId), Some(token)) =>
        Logger.info(s"Wrong user type - redirecting to login. user : ${decrypt(encryptedUserId)} token : ${token}")
        handleRedirect(request, redirectToOrigin)
    }
  }

  object AuthorisedForGovernmentGatewayAction extends AuthorisedAction {

    def handleRedirect(request: Request[AnyContent]): SimpleResult = Redirect(routes.HomeController.landing())

    def handleNotAuthorised(request: Request[AnyContent], redirectToOrigin: Boolean): PartialFunction[(Option[String], Option[String]), Result] = {
      case (None, token @ _) =>
        Logger.info(s"No identity cookie found - redirecting to login. user: None token : ${token}")
        handleRedirect(request)
      case (Some(encryptedUserId), None) =>
        Logger.info(s"No gateway token - redirecting to login. user : ${decrypt(encryptedUserId)} token : None")
        handleRedirect(request)
    }
  }

  object UnauthorisedAction {

    def apply[A <: TaxRegime](action: (Request[AnyContent] => Result)): Action[AnyContent] =
      WithHeaders {
        WithRequestLogging {
          WithRequestAuditing {
            Action {
              request =>
                action(request)
            }
          }
        }
      }
  }

  private[common] def getRegimeRootsObject(authority: UserAuthority): RegimeRoots = {

    import uk.gov.hmrc.common.microservice.ct.domain.CtDomain.CtRoot
    import uk.gov.hmrc.common.microservice.epaye.domain.EpayeDomain.EpayeRoot
    import uk.gov.hmrc.common.microservice.vat.domain.VatDomain.VatRoot
    import uk.gov.hmrc.common.microservice.sa.domain.SaDomain.SaRoot


    val regimes = authority.regimes
    RegimeRoots(
      paye = regimes.paye map {
        uri => payeMicroService.root(uri.toString)
      },
      sa = regimes.sa map {
        uri => SaRoot(authority.saUtr.get, saConnector.root(uri.toString))
      },
      vat = regimes.vat map {
        uri => VatRoot(authority.vrn.get, vatConnector.root(uri.toString))
      },
      epaye = regimes.epaye.map {
        uri => EpayeRoot(authority.empRef.get, epayeConnector.root(uri.toString))
      },
      ct = regimes.ct.map {
        uri => CtRoot(authority.ctUtr.get, ctConnector.root(uri.toString))
      },
      agent = regimes.agent.map {
        uri => agentMicroServiceRoot.root(uri.toString)
      }
    )
  }
}
