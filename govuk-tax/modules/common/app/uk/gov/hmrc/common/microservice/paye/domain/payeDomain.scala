package uk.gov.hmrc.microservice.paye.domain

import org.joda.time.LocalDate
import controllers.common.routes
import play.api.mvc.Call
import uk.gov.hmrc.microservice.paye.PayeMicroService
import uk.gov.hmrc.microservice.domain.{ TaxRegime, RegimeRoot }
import uk.gov.hmrc.microservice.auth.domain.Regimes
import uk.gov.hmrc.microservice.txqueue.{TxQueueTransaction, TxQueueMicroService}


object PayeRegime extends TaxRegime {
  override def isAuthorised(regimes: Regimes) = {
    regimes.paye.isDefined
  }

  override def unauthorisedLandingPage: String = {
    routes.LoginController.login.url
  }
}

case class PayeRoot(nino: String, version: Int, name: String, links: Map[String, String], transactionLinks: Map[String, String]) extends RegimeRoot {

  def taxCodes(implicit payeMicroService: PayeMicroService): Seq[TaxCode] = {
    resourceFor[Seq[TaxCode]]("taxCode").getOrElse(Seq.empty)
  }

  def benefits(implicit payeMicroService: PayeMicroService): Seq[Benefit] = {
    resourceFor[Seq[Benefit]]("benefits").getOrElse(Seq.empty)
  }

  def employments(implicit payeMicroService: PayeMicroService): Seq[Employment] = {
    resourceFor[Seq[Employment]]("employments").getOrElse(Seq.empty)
  }

  def transactions(resource: String)(implicit txQueueMicroService: TxQueueMicroService): Seq[TxQueueTransaction] = {
    links.get(resource) match {
      case Some(uri) => txQueueMicroService.transaction(uri).getOrElse(Seq.empty)
      case _ => Seq.empty
    }
  }

  private def resourceFor[T](resource: String)(implicit payeMicroService: PayeMicroService, m: Manifest[T]): Option[T] = {
    links.get(resource) match {
      case Some(uri) => payeMicroService.linkedResource[T](uri)
      case _ => None
    }
  }
}

case class TaxCode(taxCode: String)

case class Benefit(benefitType: Int, taxYear: Int, grossAmount: BigDecimal, employmentSequenceNumber: Int, costAmount: BigDecimal, amountMadeGood: BigDecimal, cashEquivalent: BigDecimal, expensesIncurred: BigDecimal, amountOfRelief: BigDecimal, paymentOrBenefitDescription: String, car: Option[Car], actions: Map[String, String], calculations: Map[String, String]) {
  def grossAmountToString(format: String = "%.2f") = format.format(grossAmount)
}

case class Car(dateCarMadeAvailable: Option[LocalDate], dateCarWithdrawn: Option[LocalDate], dateCarRegistered: Option[LocalDate], employeeCapitalContribution: BigDecimal, fuelType: Int, co2Emissions: Int, engineSize: Int, mileageBand: String, carValue: BigDecimal)

case class RemoveCarBenefit(version: Int, benefit: Benefit, revisedAmount: BigDecimal, withdrawDate: LocalDate)

case class Employment(sequenceNumber: Int, startDate: LocalDate, endDate: Option[LocalDate], taxDistrictNumber: String, payeNumber: String, employerName: String)

case class TransactionId(oid: String)