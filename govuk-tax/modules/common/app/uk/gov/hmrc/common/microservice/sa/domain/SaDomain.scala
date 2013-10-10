package uk.gov.hmrc.common.microservice.sa.domain

import controllers.common.FrontEndRedirect
import uk.gov.hmrc.common.microservice.sa.SaConnector
import uk.gov.hmrc.common.microservice.domain.{ TaxRegime, RegimeRoot }
import uk.gov.hmrc.common.microservice.auth.domain.Regimes
import uk.gov.hmrc.common.microservice.sa.domain.write.{TransactionId, SaAddressForUpdate}
import org.joda.time.LocalDate
import uk.gov.hmrc.domain.SaUtr

object SaRegime extends TaxRegime {
  override def isAuthorised(regimes: Regimes) = {
    regimes.sa.isDefined
  }

  override def unauthorisedLandingPage: String = {
    FrontEndRedirect.businessTaxHome
  }
}

object SaDomain {

  object SaRoot {
    def apply(utr: SaUtr, root: SaJsonRoot) = new SaRoot(utr, root.links)
  }

  case class SaJsonRoot(links: Map[String, String])

  case class SaRoot(utr: SaUtr, links: Map[String, String]) extends RegimeRoot[SaUtr] {

    private val individualDetailsKey = "individual/details"
    private val individualMainAddressKey = "individual/details/main-address"
    private val individualAccountSummaryKey = "individual/account-summary"

    override val identifier = utr

    def personalDetails(implicit saConnector: SaConnector): Option[SaPerson] = {
      links.get(individualDetailsKey) match {
        case Some(uri) => saConnector.person(uri)
        case _ => None
      }
    }

    def accountSummary(implicit saConnector: SaConnector): Option[SaAccountSummary] = {
      links.get(individualAccountSummaryKey) match {
        case Some(uri) => {
          saConnector.accountSummary(uri) match {
            case None => throw new IllegalStateException(s"Expected HOD data not found for link '$individualAccountSummaryKey' with path: $uri")
            case summary => summary
          }
        }
        case _ => None
      }
    }

    def updateIndividualMainAddress(address: SaAddressForUpdate)(implicit saConnector: SaConnector): Either[String, TransactionId] = {
      saConnector.updateMainAddress(uriFor(individualMainAddressKey), address)
    }

    private def uriFor(key: String): String = {
      links.getOrElse(key, throw new IllegalStateException("Missing link for key: " + key))
    }
  }

  case class SaPerson(name: SaName, address: SaIndividualAddress)

  case class SaIndividualAddress(
    addressLine1: String,
    addressLine2: String,
    addressLine3: Option[String],
    addressLine4: Option[String],
    addressLine5: Option[String],
    postcode: Option[String],
    foreignCountry: Option[String],
    additionalDeliveryInformation: Option[String])

  case class  SaName(
     title: String,
     forename: String,
     secondForename: Option[String],
     surname: String,
     honours: Option[String])

  case class Liability(dueDate: LocalDate, amount: BigDecimal)

  case class AmountDue(amount: BigDecimal, requiresPayment: Boolean)

  case class SaAccountSummary(totalAmountDueToHmrc: Option[AmountDue], nextPayment: Option[Liability], amountHmrcOwe: Option[BigDecimal])
}

