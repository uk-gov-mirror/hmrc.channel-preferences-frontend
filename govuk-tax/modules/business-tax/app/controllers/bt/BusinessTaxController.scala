package controllers.bt

import controllers.common._
import uk.gov.hmrc.common.PortalUrlBuilder
import controllers.bt.accountsummary._
import uk.gov.hmrc.common.microservice.domain.User

class BusinessTaxController(accountSummaryFactory: AccountSummariesFactory)
  extends BaseController
  with ActionWrappers
  with PortalUrlBuilder {

  def home = ActionAuthorisedBy(GovernmentGateway)() {
    implicit user =>
      implicit request =>
        Ok(
          businessTaxHomepage(accountSummaries = accountSummaryFactory.create(buildPortalUrl))
        )
  }

  def makeAPaymentLanding = ActionAuthorisedBy(GovernmentGateway)() {
    implicit user =>
      implicit request =>
        Ok(makeAPaymentLandingPage())
  }

   private[bt] def makeAPaymentLandingPage()(implicit user: User) =
    views.html.make_a_payment_landing()

  private[bt] def businessTaxHomepage(accountSummaries: AccountSummaries)(implicit user: User) =
    views.html.business_tax_home(accountSummaries)


  def this() = this(new AccountSummariesFactory())
}
