package controllers.common

import controllers.common.actions.{HeaderCarrier, Actions}
import uk.gov.hmrc.common.microservice.auth.AuthConnector
import uk.gov.hmrc.common.microservice.audit.AuditConnector
import controllers.common.service.Connectors
import play.api.data._
import play.api.data.Forms._
import play.api.libs.json._
import play.api.mvc.Request
import uk.gov.hmrc.common.microservice.deskpro.HmrcDeskproConnector
import play.api.i18n.Messages
import scala.concurrent.Future
import uk.gov.hmrc.common.microservice.deskpro.domain.TicketId


class ProblemReportsController(override val auditConnector: AuditConnector, hmrcDeskproConnector: HmrcDeskproConnector)(implicit override val authConnector: AuthConnector)
  extends BaseController
  with Actions
  with AllRegimeRoots {

  def this() = this(Connectors.auditConnector, Connectors.hmrcDeskproConnector)(Connectors.authConnector)

  val form = Form[ProblemReport](
    mapping(
      "report-name" -> text
        .verifying("error.common.problem_report.action_mandatory", action => !action.isEmpty)
        .verifying("error.common.problem_report.name_too_long", name => name.size <= 70),
      "report-email" -> email.verifying("error.email_too_long", email => email.size <= 320),
      "report-action" -> text
        .verifying("error.common.problem_report.action_mandatory", action => !action.isEmpty)
        .verifying("error.common.comments_too_long", action => action.size <= 1000),
      "report-error" -> text
        .verifying("error.common.problem_report.action_mandatory", error => !error.isEmpty)
        .verifying("error.common.comments_too_long", error => error.size <= 1000),
      "isJavascript" -> boolean
    )(ProblemReport.apply)(ProblemReport.unapply)
  )


  def report = WithNewSessionTimeout(UnauthorisedAction.async {
    implicit request => {
      form.bindFromRequest.fold(
        error => {
          if (!error.data.getOrElse("isJavascript", "true").toBoolean) {
            Future.successful(Ok(views.html.problem_reports_error_nonjavascript(referrerFrom(request))))
          } else {
            Future.successful(BadRequest(Json.toJson(Map("status" -> "ERROR"))))
          }
        },
        problemReport => {
          createTicket(problemReport, request).map {
            ticketOption =>
              if (!problemReport.isJavascript) {
                Ok(views.html.problem_reports_confirmation_nonjavascript())
              } else {
                val ticket = ticketOption.map(_.ticket_id).getOrElse("Unknown")
                Ok(Json.toJson(
                  Map("status" -> "OK",
                    "message" -> s"""<h2 id="feedback-thank-you-header">Thank you for your help. Your support reference number is <span id="ticketId">$ticket</span></h2> <p>If you have more extensive feedback, please visit the <a href='/contact'>contact page</a>.</p>"""
                  )
                ))
              }
          }
        })
    }
  })

  private def createTicket(problemReport: ProblemReport, request: Request[AnyRef]): Future[Option[TicketId]] = {
    import ProblemReportsController._
    implicit val hc = HeaderCarrier(request)
    hmrcDeskproConnector.createTicket(
      problemReport.reportName,
      problemReport.reportEmail,
      "Support Request",
      problemMessage(problemReport.reportAction, problemReport.reportError),
      referrerFrom(request),
      problemReport.isJavascript,
      request,
      None
    )
  }

  private def referrerFrom(request: Request[AnyRef]): String = {
    request.headers.get("referer").getOrElse("/home")
  }
}

object ProblemReportsController {

  def problemMessage(action: String, error: String): String = {
    s"""
    ${Messages("problem_report.action")}:
    $action

    ${Messages("problem_report.error")}:
    $error
    """
  }

}

case class ProblemReport(reportName: String, reportEmail: String, reportAction: String, reportError: String, isJavascript: Boolean)