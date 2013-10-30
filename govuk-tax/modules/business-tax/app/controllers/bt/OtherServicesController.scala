package controllers.bt

import controllers.bt.otherservices.{OtherServicesFactory, OtherServicesSummary}
import controllers.common.{GovernmentGateway, ActionWrappers, BaseController}
import uk.gov.hmrc.common.PortalUrlBuilder
import uk.gov.hmrc.common.microservice.domain.User
import play.api.templates.Html
import controllers.common.service.MicroServices


class OtherServicesController(otherServicesFactory: OtherServicesFactory)
  extends BaseController
  with ActionWrappers
  with PortalUrlBuilder {

  def this() = this(new OtherServicesFactory(MicroServices.governmentGatewayMicroService))

  def otherServices = ActionAuthorisedBy(GovernmentGateway)() {
    implicit user =>
      implicit request =>
        Ok(otherServicesPage(
          OtherServicesSummary(
            otherServicesFactory.createManageYourTaxes(buildPortalUrl),
            otherServicesFactory.createOnlineServicesEnrolment(buildPortalUrl),
            otherServicesFactory.createBusinessTaxesRegistration(buildPortalUrl)
          )
        ))
  }

  private[bt] def otherServicesPage(otherServicesSummary: OtherServicesSummary)(implicit user: User): Html =
    views.html.other_services(otherServicesSummary)
}
