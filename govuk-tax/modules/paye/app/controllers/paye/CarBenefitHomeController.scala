package controllers.paye

import controllers.common.{Ida, Actions, BaseController2}
import play.api.mvc.{SimpleResult, Request}
import uk.gov.hmrc.common.microservice.paye.domain.{TaxYearData, Employment, PayeRegime}
import uk.gov.hmrc.common.microservice.paye.domain.Employment._
import models.paye.{EmploymentView, EmploymentViews, BenefitTypes}
import play.api.Logger
import uk.gov.hmrc.utils.TaxYearResolver
import controllers.common.validators.Validators
import uk.gov.hmrc.common.microservice.domain.User
import controllers.common.service.Connectors
import uk.gov.hmrc.common.microservice.audit.AuditConnector
import uk.gov.hmrc.common.microservice.auth.AuthConnector
import uk.gov.hmrc.common.microservice.paye.PayeConnector
import uk.gov.hmrc.common.microservice.txqueue.TxQueueConnector

class CarBenefitHomeController(override val auditConnector: AuditConnector, override val authConnector: AuthConnector)(implicit payeService: PayeConnector, txQueueMicroservice: TxQueueConnector) extends BaseController2
  with Actions
  with Validators {

  private[paye] def currentTaxYear = TaxYearResolver.currentTaxYear

  def this() = this(Connectors.auditConnector, Connectors.authConnector)(Connectors.payeConnector, Connectors.txQueueConnector)

  def carBenefitHome = ActionAuthorisedBy(Ida)(taxRegime = Some(PayeRegime), redirectToOrigin = true) {
    implicit user =>
      implicit request =>
        carBenefitHomeAction
  }

  private[paye] def carBenefitHomeAction(implicit user: User, request: Request[_]): SimpleResult = {
    val payeRoot = user.getPaye
    val currentTaxYearData = payeRoot.fetchTaxYearData(currentTaxYear)

    findPrimaryEmployment(currentTaxYearData) match {
      case Some(employment) => {

        val carBenefit = currentTaxYearData.findExistingBenefit(employment.sequenceNumber, BenefitTypes.CAR)
        val fuelBenefit = currentTaxYearData.findExistingBenefit(employment.sequenceNumber, BenefitTypes.FUEL)

        val employmentViews = EmploymentViews(payeRoot.fetchEmployments(currentTaxYear), payeRoot.fetchTaxCodes(currentTaxYear), currentTaxYear, payeRoot.fetchRecentAcceptedTransactions(), payeRoot.fetchRecentCompletedTransactions())
        Ok(views.html.paye.car_benefit_home(carBenefit, fuelBenefit, employment.employerName, employment.sequenceNumber, currentTaxYear, employmentViews))
      }
      case None => {
        Logger.debug(s"Unable to find current employment for user ${user.oid}")
        InternalServerError
      }
    }
  }

  private def findPrimaryEmployment(payeRootData: TaxYearData): Option[Employment] = {
    payeRootData.employments.find(_.employmentType == primaryEmploymentType)
  }
}
