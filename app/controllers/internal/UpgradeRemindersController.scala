package controllers.internal

import config.Global
import connectors._
import controllers.{Authentication, FindTaxIdentifier}
import model.Encrypted
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{EventTypes, ExtendedDataEvent}
import uk.gov.hmrc.play.config.AppName
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.frontend.auth.{Actions, AuthContext}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier
import views.html.sa.prefs.{upgrade_printing_preferences, upgrade_printing_preferences_thank_you}

import scala.concurrent.Future


object UpgradeRemindersController extends UpgradeRemindersController with Authentication{
  override def authConnector = Global.authConnector
  def entityResolverConnector = EntityResolverConnector

  override def auditConnector: AuditConnector = Global.auditConnector

  def displayUpgradeForm(encryptedReturnUrl: Encrypted[String]): Action[AnyContent] = authenticated.async {
    authContext => implicit request =>
      _renderUpgradePageIfPreferencesAvailable(authContext, encryptedReturnUrl)
  }

  def submitUpgrade(returnUrl: Encrypted[String]) = authenticated.async { authContext => implicit request =>
    _upgradePreferences(
      returnUrl = returnUrl.decryptedValue, authContext = authContext
    )
  }

  def displayUpgradeConfirmed(returnUrl: Encrypted[String]): Action[AnyContent] = authenticated.async {
    authContext => implicit request =>
      _displayConfirm(returnUrl)
  }
}

object UpgradeRemindersForm {
  def apply() = Form[Data](mapping(
    "opt-in" -> optional(boolean).verifying("sa_printing_preference.opt_in_choice_required", _.isDefined).transform[Boolean](_.get, Some(_)),
    "accept-tc" -> boolean
  )(Data.apply)(Data.unapply))

  case class Data(optIn: Boolean, acceptTandC: Boolean)
}

trait UpgradeRemindersController extends FrontendController with Actions with AppName with FindTaxIdentifier {

  def authConnector: AuthConnector
  def entityResolverConnector: EntityResolverConnector
  def auditConnector: AuditConnector

  private[controllers] def _renderUpgradePageIfPreferencesAvailable(authContext: AuthContext, encryptedReturnUrl: Encrypted[String])(implicit request: Request[AnyContent]): Future[Result] = {
    decideRoutingFromPreference(authContext, encryptedReturnUrl, UpgradeRemindersForm())
  }

  private[controllers] def _upgradePreferences(returnUrl:String, authContext: AuthContext)(implicit request: Request[AnyContent]): Future[Result] = {
    val form = UpgradeRemindersForm().bindFromRequest()
    form.fold(
      hasErrors = f => Future(BadRequest(upgrade_printing_preferences(None, Encrypted(returnUrl), f))),
      success = {
        case u @ UpgradeRemindersForm.Data(true, false) => Future(BadRequest(upgrade_printing_preferences(None, Encrypted(returnUrl), form.withError("accept-tc", "sa_printing_preference.accept_tc_required"))))
        case UpgradeRemindersForm.Data(true, true) => upgradePaperless(authContext, Generic -> TermsAccepted(true)).map {
          case true => Redirect(routes.UpgradeRemindersController.displayUpgradeConfirmed(Encrypted(returnUrl)))
          case false => Redirect(returnUrl)
        }
        case UpgradeRemindersForm.Data(false, _) => upgradePaperless(authContext, Generic -> TermsAccepted(false)).map(resp => Redirect(returnUrl))
      }
    )
  }

  private def decideRoutingFromPreference(authContext: AuthContext, encryptedReturnUrl: Encrypted[String], tandcForm:Form[UpgradeRemindersForm.Data])(implicit request: Request[AnyContent]) = {
    entityResolverConnector.getPreferences(findTaxIdentifier(authContext)).map {
      case Some(prefs) => Ok(upgrade_printing_preferences(prefs.email.map(e => e.email), encryptedReturnUrl, tandcForm))
      case None => Redirect(encryptedReturnUrl.decryptedValue)
    }
  }

  private[controllers] def upgradePaperless(authContext: AuthContext, termsAccepted: (TermsType, TermsAccepted))(implicit request: Request[AnyContent], hc: HeaderCarrier) : Future[Boolean] =
    entityResolverConnector.updateTermsAndConditions(findTaxIdentifier(authContext), termsAccepted, email = None).map { status =>
      val isSuccessful = status != PreferencesFailure
      if (isSuccessful) auditChoice(authContext, termsAccepted, status)
      isSuccessful
    }

  private def auditChoice(authContext: AuthContext, terms: (TermsType, TermsAccepted), preferencesStatus: PreferencesStatus)(implicit request: Request[_], hc: HeaderCarrier) =
    auditConnector. sendEvent(ExtendedDataEvent(
      auditSource = appName,
      auditType = EventTypes.Succeeded,
      tags = hc.toAuditTags("Set Print Preference", request.path),
      detail = Json.toJson(hc.toAuditDetails(
        "client" -> "",
        "nino" -> findNino(authContext).map(_.nino).getOrElse("N/A"),
        "utr" -> findUtr(authContext).map(_.utr).getOrElse("N/A"),
        "TandCsScope" -> terms._1.toString.toLowerCase,
        "userConfirmedReadTandCs" -> terms._2.accepted.toString,
        "journey" -> "",
        "digital" -> terms._2.accepted.toString,
        "cohort" -> "",
        "newUserPreferencesCreated" -> (preferencesStatus == PreferencesCreated).toString
      ))))

  private[controllers] def _displayConfirm(returnUrl: Encrypted[String])(implicit request: Request[_], hc: HeaderCarrier) =
    Future(Ok(upgrade_printing_preferences_thank_you(returnUrl.decryptedValue)))

}
