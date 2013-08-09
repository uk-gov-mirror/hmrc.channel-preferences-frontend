package controllers

import play.api.data._
import play.api.data.Forms._
import play.api.mvc.Action

class AgentController extends BaseController with ActionWrappers {

  def reasonForApplication() = UnauthorisedAction { implicit request =>
    Ok(views.html.agents.reason_for_application())
  }

  val userForm = Form[SroCheck](
    mapping(
      "sroAgreement" -> checked("error.agent.accept.sroAgreement"),
      "tncAgreement" -> checked("error.agent.accept.tncAgreement")
    )(SroCheck.apply)(SroCheck.unapply)
  )

  def sroCheck() = UnauthorisedAction { implicit request =>
    Ok(views.html.agents.sro_check(userForm))
  }

  def submitAgreement = UnauthorisedAction {
    implicit request =>
      userForm.bindFromRequest.fold(
        errors => {
          BadRequest(views.html.agents.sro_check(errors))
        },
        _ => {
          Redirect(routes.LoginController.samlLogin).withSession(session.copy(Map("register agent" -> "true")))
        }
      )
  }

  def contactDetails = Action {
    Ok(views.html.agents.contact_details())
  }

}

case class SroCheck(sroAgreement: Boolean = false, tncAgreement: Boolean = false)

