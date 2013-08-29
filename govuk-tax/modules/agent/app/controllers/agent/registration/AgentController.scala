package controllers.agent.registration

import uk.gov.hmrc.microservice.domain.User

trait AgentController {

  def userId(user: User) = { user.user.substring(user.user.lastIndexOf("/") + 1) }

  def registrationId(user: User) = "Registration:" + userId(user)
  def uar(user: User) = "UAR:" + userId(user)

  val agent = "agent"
  val uar = "uar"
}

object FormNames {
  val professionalBodyMembershipFormName = "professionalBodyMembershipForm"
  val companyDetailsFormName = "companyDetailsForm"
  val agentTypeAndLegalEntityFormName = "agentTypeAndLegalEntityForm"
  val contactFormName = "contactForm"
}
