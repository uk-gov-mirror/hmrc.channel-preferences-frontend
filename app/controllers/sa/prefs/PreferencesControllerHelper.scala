package controllers.sa.prefs

import play.api.mvc.{SimpleResult, Call, Request}
import controllers.common.actions.HeaderCarrier
import play.api.data._
import play.api.mvc.Results._
import play.api.data.Forms._
import uk.gov.hmrc.common.microservice.email.EmailConnector
import uk.gov.hmrc.common.microservice.preferences.FormattedUri
import scala.concurrent._
import uk.gov.hmrc.common.MdcLoggingExecutionContext.fromLoggingDetails
import Function.const
import uk.gov.hmrc.domain.{Email, SaUtr}
import play.api.templates.HtmlFormat

trait PreferencesControllerHelper {

  protected val emailForm: Form[EmailPreferenceData] = Form[EmailPreferenceData](mapping(
    "email" ->
      tuple(
        "main" -> email.verifying("error.email_too_long", email => email.size < 320),
        "confirm" -> optional(text)
      ).verifying(
        "email.confirmation.emails.unequal", email => email._1 == email._2.getOrElse("")
      ),
    "emailVerified" -> optional(text)
  )(EmailPreferenceData.apply)(EmailPreferenceData.unapply))

  def getSubmitPreferencesView(savePrefsCall: Call, keepPaperCall: Call)(implicit request: Request[AnyRef], withBanner: Boolean = false): Form[EmailPreferenceData] => HtmlFormat.Appendable = {
    errors => views.html.sa.prefs.sa_printing_preference(withBanner, errors, savePrefsCall, keepPaperCall)
  }

  def displayPreferencesForm(email: Option[Email], savePrefsCall: Call, keepPaperCall: Call)(implicit request: Request[AnyRef]) = {
    Ok(views.html.sa.prefs.sa_printing_preference(false,
      emailForm = emailForm.fill(EmailPreferenceData(email)),
      submitPrefsFormAction = savePrefsCall,
      submitKeepPaperAction = keepPaperCall))
  }

  protected def submitPreferencesForm(errorsView: (Form[EmailPreferenceData]) => play.api.templates.HtmlFormat.Appendable,
                                      emailWarningView: (String) => play.api.templates.HtmlFormat.Appendable,
                                      successRedirect: () => Call,
                                      emailConnector: EmailConnector,
                                      saUtr: SaUtr,
                                      savePreferences: (SaUtr, Boolean, Option[String], HeaderCarrier) => Future[Option[FormattedUri]])
                                     (implicit request: Request[AnyRef]): Future[SimpleResult] = {

    implicit def hc = HeaderCarrier(request)

    emailForm.bindFromRequest()(request).fold(
      errors => Future.successful(BadRequest(errorsView(errors))),
      emailForm => {
        val emailVerificationStatus =
          if (emailForm.isEmailVerified) Future.successful(true)
          else emailConnector.validateEmailAddress(emailForm.mainEmail)

        emailVerificationStatus.flatMap {
          case true => savePreferences(saUtr, true, Some(emailForm.mainEmail), hc).map(const(Redirect(successRedirect())))
          case false => Future.successful(Ok(emailWarningView(emailForm.mainEmail)))
        }
      }
    )
  }

}