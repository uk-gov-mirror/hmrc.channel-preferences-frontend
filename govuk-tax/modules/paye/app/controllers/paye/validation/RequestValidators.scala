package controllers.paye.validation

import play.api.mvc.{SimpleResult, Request}
import uk.gov.hmrc.common.microservice.domain.User
import uk.gov.hmrc.common.microservice.paye.domain.{BenefitTypes, Employment, TaxYearData}
import play.api.Logger
import controllers.paye.routes
import play.api.mvc.Results._
import uk.gov.hmrc.common.microservice.paye.domain.Employment._
import uk.gov.hmrc.common.microservice.paye.PayeConnector
import uk.gov.hmrc.common.microservice.txqueue.TxQueueConnector
import controllers.common.actions.HeaderCarrier

object WithValidatedCarRequest{
  def apply(action: (Request[_], User, Int, Int, TaxYearData) => SimpleResult)
           (implicit payeConnector:PayeConnector, txQueueConnector:TxQueueConnector, currentTaxYear:Int): (User, Request[_], Int, Int) => SimpleResult = WithValidatedRequest(BenefitTypes.CAR, action)
}

object WithValidatedFuelRequest{
  def apply(action: (Request[_], User, Int, Int, TaxYearData) => SimpleResult)
           (implicit payeConnector:PayeConnector, txQueueConnector:TxQueueConnector, currentTaxYear:Int): (User, Request[_], Int, Int) => SimpleResult = WithValidatedRequest(BenefitTypes.FUEL, action)
}

object WithValidatedRequest{

  def apply(benefitType:Int, action: (Request[_], User, Int, Int, TaxYearData) => SimpleResult)(implicit payeConnector:PayeConnector, txQueueConnector:TxQueueConnector, currentTaxYear:Int): (User, Request[_], Int, Int) => SimpleResult = {

    (user, request, taxYear, employmentSequenceNumber) => {

      implicit val hc = HeaderCarrier(request)
      if (currentTaxYear != taxYear) {
        Logger.error("Adding fuel benefit is only allowed for the current tax year")
        BadRequest
      } else {
        val payeRootData = user.regimes.paye.get.fetchTaxYearData(currentTaxYear)

        if (employmentSequenceNumber != findPrimaryEmployment(payeRootData).get.sequenceNumber) {
          Logger.error("Adding fuel benefit is only allowed for the primary employment")
          BadRequest
        } else {
          if (payeRootData.findExistingBenefit(employmentSequenceNumber, benefitType).isDefined) {
            redirectToCarBenefitHome(request, user)
          } else {
            action(request, user, taxYear, employmentSequenceNumber, payeRootData)
          }
        }
      }
    }
  }
  private def findPrimaryEmployment(payeRootData: TaxYearData): Option[Employment] = payeRootData.employments.find(_.employmentType == primaryEmploymentType)

  private val redirectToCarBenefitHome: (Request[_], User) => SimpleResult = (r, u) => Redirect(routes.CarBenefitHomeController.carBenefitHome().url)
}





