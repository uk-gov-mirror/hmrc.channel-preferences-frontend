package uk.gov.hmrc.common.microservice.paye

import org.joda.time.LocalDate
import views.formatting.Dates
import uk.gov.hmrc.common.microservice.paye.domain._
import uk.gov.hmrc.microservice.{TaxRegimeConnector, MicroServiceConfig}
import controllers.common.domain.Transform._
import play.api.libs.json.Json
import uk.gov.hmrc.common.microservice.paye.domain.PayeRoot
import uk.gov.hmrc.common.microservice.paye.domain.Benefit
import uk.gov.hmrc.common.microservice.paye.domain.RemoveBenefit
import controllers.common.actions.HeaderCarrier

class PayeConnector extends TaxRegimeConnector[PayeRoot] {

  override val serviceUrl = MicroServiceConfig.payeServiceUrl

  def root(uri: String)(implicit hc: HeaderCarrier) = httpGet[PayeRoot](uri).getOrElse(throw new IllegalStateException(s"Expected Paye root not found at URI '$uri'"))

  def addBenefits(uri: String,
    version: Int,
    employmentSequenceNumber:Int,
    benefits: Seq[Benefit])(implicit hc: HeaderCarrier) : Option[AddBenefitResponse] = {

    httpPost[AddBenefitResponse](
      uri,
      body = Json.parse(
        toRequestBody(
          AddBenefit(
            version = version,
            employmentSequence = employmentSequenceNumber,
            benefits = benefits)
        )
      )
    )
  }

  def removeBenefits(uri: String,
    version: Int,
    benefits: Seq[RevisedBenefit],
    dateCarWithdrawn: LocalDate)(implicit hc: HeaderCarrier) = {
    httpPost[RemoveBenefitResponse](
      uri,
      body = Json.parse(
        toRequestBody(
          RemoveBenefit(
            version = version,
            benefits = benefits,
            withdrawDate = dateCarWithdrawn)
        )
      )
    )
  }

  def calculateBenefitValue(uri: String, carAndFuel: CarAndFuel)(implicit hc: HeaderCarrier): Option[NewBenefitCalculationResponse] = {
    httpPost[NewBenefitCalculationResponse](
      uri,
      body = Json.parse(
        toRequestBody(
          carAndFuel
        )
      )
    )
  }

  def calculationWithdrawKey():String = "withdraw"
  def calculateWithdrawBenefit(benefit: Benefit, withdrawDate: LocalDate)(implicit hc: HeaderCarrier) = {
    httpGet[RemoveBenefitCalculationResponse](benefit.calculations(calculationWithdrawKey()).replace("{withdrawDate}", Dates.shortDate(withdrawDate))).get
  }
}
