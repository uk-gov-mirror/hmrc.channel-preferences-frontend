package controllers.sa.prefs.internal

import connectors.{EmailConnector, PreferencesConnector, SaPreference}
import controllers.common.BaseController
import controllers.sa.Encrypted
import controllers.sa.prefs.{EmailFormData, SaRegime}
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.auth.frontend.connectors.AuthConnector
import uk.gov.hmrc.play.config.AuditConnector
import uk.gov.hmrc.play.frontend.auth.{Actions, User}

import scala.concurrent.Future

object AccountDetailsController extends AccountDetailsController {
  lazy val auditConnector = AuditConnector
  lazy val authConnector = AuthConnector
  lazy val emailConnector = EmailConnector
  lazy val preferencesConnector = PreferencesConnector
}

trait AccountDetailsController
  extends BaseController
  with Actions
  with PreferencesControllerHelper {

  val auditConnector: AuditConnector
  val authConnector: AuthConnector
  val emailConnector: EmailConnector
  val preferencesConnector: PreferencesConnector

  def changeEmailAddress(emailAddress: Option[Encrypted[EmailAddress]]) = AuthorisedFor(regime = SaRegime).async {
    user => request => changeEmailAddressPage(emailAddress)(user, request)
  }

  def submitEmailAddress = AuthorisedFor(regime = SaRegime).async {
    user => request => submitEmailAddressPage(user, request)
  }

  def emailAddressChangeThankYou() = AuthorisedFor(regime = SaRegime).async {
    user => request => emailAddressChangeThankYouPage(user, request)
  }

  def optOutOfEmailReminders = AuthorisedFor(regime = SaRegime).async {
    user => request => optOutOfEmailRemindersPage(user, request)
  }

  def confirmOptOutOfEmailReminders = AuthorisedFor(regime = SaRegime).async {
    user => request => confirmOptOutOfEmailRemindersPage(user, request)
  }

  def optedBackIntoPaperThankYou() = AuthorisedFor(regime = SaRegime).async {
    implicit user => implicit request => Future(Ok(views.html.opted_back_into_paper_thank_you()))
  }

  def resendValidationEmail() = AuthorisedFor(regime = SaRegime).async {
    user => request => resendValidationEmailAction(user, request)
  }

  private[prefs] def confirmOptOutOfEmailRemindersPage(implicit user: User, request: Request[AnyRef]): Future[Result] = {
    lookupCurrentEmail {
      email =>
        preferencesConnector.savePreferences(user.userAuthority.accounts.sa.get.utr, false, None).map(_ =>
          Redirect(routes.AccountDetailsController.optedBackIntoPaperThankYou())
        )
    }
  }

  private[prefs] def resendValidationEmailAction(implicit user: User, request: Request[AnyRef]): Future[Result] = {
    lookupCurrentEmail {
      email =>
        preferencesConnector.savePreferences(user.userAuthority.accounts.sa.get.utr, true, Some(email)).map(_ =>
          Ok(views.html.account_details_verification_email_resent_confirmation(email))
        )
    }
  }

  private[prefs] def optOutOfEmailRemindersPage(implicit user: User, request: Request[AnyRef]) = 
    lookupCurrentEmail(email => Future.successful(Ok(views.html.confirm_opt_back_into_paper(email.obfuscated))))

  private[prefs] def changeEmailAddressPage(emailAddress: Option[Encrypted[EmailAddress]])(implicit user: User, request: Request[AnyRef]): Future[Result] = 
    lookupCurrentEmail(email => Future.successful(Ok(views.html.account_details_update_email_address(email, emailForm.fill(EmailFormData(emailAddress.map(_.decryptedValue)))))))



  private def lookupCurrentEmail(func: (EmailAddress) => Future[Result])(implicit user: User, request: Request[AnyRef]): Future[Result] = {
    preferencesConnector.getPreferences(user.userAuthority.accounts.sa.get.utr)(HeaderCarrier.fromSessionAndHeaders(request.session, request.headers)).flatMap {
        case Some(SaPreference(true, Some(email))) => func(EmailAddress(email.email))
        case _ => Future.successful(BadRequest("Could not find existing preferences."))
    }
  }

  private[prefs] def submitEmailAddressPage(implicit user: User, request: Request[AnyRef]): Future[Result] = 
    lookupCurrentEmail(
      email =>
        submitEmailForm(
          views.html.account_details_update_email_address(email, _),
          (enteredEmail) => views.html.account_details_update_email_address_verify_email(enteredEmail),
          () => routes.AccountDetailsController.emailAddressChangeThankYou(),
          emailConnector,
          user.userAuthority.accounts.sa.get.utr,
          savePreferences
        )
    )

  private def savePreferences(utr: SaUtr, digital: Boolean, email: Option[String] = None, hc: HeaderCarrier) =
    preferencesConnector.savePreferences(utr, digital, email)(hc)

  private[prefs] def emailAddressChangeThankYouPage(implicit user: User, request: Request[AnyRef]): Future[Result] = {
    lookupCurrentEmail(email => Future.successful(Ok(views.html.account_details_update_email_address_thank_you(email.obfuscated))))
  }
}
