package uk.gov.hmrc.common.microservice.paye

import uk.gov.hmrc.common.microservice.paye.domain._
import uk.gov.hmrc.microservice.{TaxRegimeConnector, MicroServiceConfig}
import uk.gov.hmrc.common.microservice.paye.domain.PayeRoot
import uk.gov.hmrc.common.microservice.paye.domain.Benefit
import controllers.common.actions.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PayeConnector extends TaxRegimeConnector[PayeRoot] {
  override val serviceUrl = MicroServiceConfig.payeServiceUrl

  def root(uri: String)(implicit hc: HeaderCarrier): Future[PayeRoot] =
    httpGetF[PayeRoot](uri).map(_.getOrElse(throw new IllegalStateException(s"Expected Paye root not found at URI '$uri'")))

  def addBenefits(uri: String,
                  version: Int,
                  employmentSequenceNumber: Int,
                  benefits: Seq[Benefit])(implicit hc: HeaderCarrier): Future[Option[AddBenefitResponse]] = {
    httpPostF[AddBenefit, AddBenefitResponse](uri, AddBenefit(version,employmentSequenceNumber,benefits))
  }

  def removeBenefits(uri: String, withdrawBenefitRequest: WithdrawnBenefitRequest)(implicit hc: HeaderCarrier) = {
    httpPostF[WithdrawnBenefitRequest, RemoveBenefitResponse](uri, withdrawBenefitRequest)
  }
}

object PayeConnector {
  val calculationWithdrawKey: String = "withdraw"
}
