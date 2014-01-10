package controllers.paye

import controllers.common.{SessionKeys, BaseController}
import uk.gov.hmrc.common.microservice.paye.domain._
import controllers.common.validators.Validators
import controllers.common.service.Connectors
import uk.gov.hmrc.common.microservice.auth.AuthConnector
import uk.gov.hmrc.common.microservice.audit.AuditConnector
import play.api.mvc.Request
import controllers.paye.validation.{BenefitFlowHelper, AddBenefitFlow}
import uk.gov.hmrc.common.microservice.paye.PayeConnector
import uk.gov.hmrc.common.microservice.txqueue.TxQueueConnector
import play.api.Logger
import play.api.data.Forms._
import play.api.data.Form
import FuelBenefitFormFields._
import controllers.paye.validation.AddCarBenefitValidator._
import org.joda.time.LocalDate
import controllers.common.actions.{HeaderCarrier, Actions}
import uk.gov.hmrc.common.microservice.paye.domain.AddFuelBenefitConfirmationData
import uk.gov.hmrc.common.microservice.paye.domain.TaxYearData
import scala.Some
import play.api.mvc.SimpleResult
import uk.gov.hmrc.common.microservice.domain.User
import controllers.paye.validation.AddCarBenefitValidator.CarBenefitValues
import models.paye.{CarBenefitBuilder, TaxCodeResolver, BenefitUpdatedConfirmationData}
import uk.gov.hmrc.common.microservice.keystore.KeyStoreConnector
import views.html.paye.add_car_benefit_confirmation
import controllers.paye.AddFuelBenefitController.FuelBenefitDataWithGrossBenefit
import scala.concurrent._


object AddFuelBenefitController {
  type FuelBenefitDataWithGrossBenefit = (FuelBenefitData)
}

class AddFuelBenefitController(keyStoreService: KeyStoreConnector, override val auditConnector: AuditConnector, override val authConnector: AuthConnector)
                              (implicit payeConnector: PayeConnector, txQueueConnector: TxQueueConnector) extends BaseController
with Actions
with Validators
with TaxYearSupport
with PayeRegimeRoots {

  def this() = this(Connectors.keyStoreConnector, Connectors.auditConnector, Connectors.authConnector)(Connectors.payeConnector, Connectors.txQueueConnector)

  private val keystoreKey = "AddFuelBenefitForm"

  def startAddFuelBenefit(taxYear: Int, employmentSequenceNumber: Int) =
    AuthorisedFor(account = PayeRegime, redirectToOrigin = true).async {
      user => request => startAddFuelBenefitAction(user, request, taxYear, employmentSequenceNumber)
    }

  def reviewAddFuelBenefit(taxYear: Int, employmentSequenceNumber: Int) =
    AuthorisedFor(PayeRegime).async {
      user => request => reviewAddFuelBenefitAction(user, request, taxYear, employmentSequenceNumber)
    }

  def confirmAddingBenefit(taxYear: Int, employmentSequenceNumber: Int) =
    AuthorisedFor(PayeRegime).async {
      user =>
        implicit request =>
          confirmAddFuelBenefitAction(user, request, taxYear, employmentSequenceNumber).removeSessionKey(SessionKeys.npsVersion)
    }

  private def fuelBenefitForm(values: CarBenefitValues) = Form[FuelBenefitData](
    mapping(
      employerPayFuel -> validateEmployerPayFuel(values),
      dateFuelWithdrawn -> validateDateFuelWithdrawn(values, taxYearInterval)
    )(FuelBenefitData.apply)(FuelBenefitData.unapply)
  )

  private def validationLessForm() = Form[EmployerPayeFuelString](
    mapping(
      employerPayFuel -> optional(text)
    )(EmployerPayeFuelString.apply)(EmployerPayeFuelString.unapply)
  )

  private[paye] def startAddFuelBenefitAction: (User, Request[_], Int, Int) => Future[SimpleResult] = AddBenefitFlow(BenefitTypes.FUEL) {
    (user, request, taxYear, employmentSequenceNumber, payeRootData) =>
      implicit def hc = HeaderCarrier(request)
      findEmployment(employmentSequenceNumber, payeRootData) match {
        case Some(employment) => {
          initialFuelBenefitValues(user, taxYear, employmentSequenceNumber).map {
            values =>
              val (initialFuelValues) = values
              val form = fuelBenefitForm(CarBenefitValues(employerPayFuel = initialFuelValues.employerPayFuel)).fill(initialFuelValues)

              Ok(views.html.paye.add_fuel_benefit_form(form, taxYear, employmentSequenceNumber, employment.employerName, currentTaxYearYearsRange)(user,request))
          }
        }
        case None => {
          Logger.debug(s"Unable to find employment for user ${user.oid} with sequence number $employmentSequenceNumber")
          Future.successful(BadRequest)
        }
      }
  }

  def initialFuelBenefitValues(user: User, taxYear: Int, employmentSequenceNumber: Int)(implicit hc: HeaderCarrier): Future[FuelBenefitDataWithGrossBenefit] = {
    keyStoreService.getEntry[FuelBenefitDataWithGrossBenefit](generateKeystoreActionId(taxYear, employmentSequenceNumber), KeystoreUtils.source, keystoreKey)
      .map(_.getOrElse((FuelBenefitData(None, None))))
  }

  private[paye] def reviewAddFuelBenefitAction: (User, Request[_], Int, Int) => Future[SimpleResult] = AddBenefitFlow(BenefitTypes.FUEL) {
    (user, request, taxYear, employmentSequenceNumber, payeRootData) =>
      findEmployment(employmentSequenceNumber, payeRootData) match {
        case Some(employment) => {
          val validationLesForm = validationLessForm().bindFromRequest()(request)

          //TODO: Why is the car provided from the start of the tax year?
          val values = CarBenefitValues(providedFromVal = Some(startOfCurrentTaxYear), employerPayFuel = validationLesForm.get.employerPayFuel)
          fuelBenefitForm(values).bindFromRequest()(request).fold(
            errors =>
              Future.successful(BadRequest(views.html.paye.add_fuel_benefit_form(errors, taxYear, employmentSequenceNumber, employment.employerName, currentTaxYearYearsRange)(user,request))),
            addFuelBenefitData => {
              implicit val hc = HeaderCarrier(request)
              val carBenefit = retrieveCarBenefit(payeRootData, employmentSequenceNumber)
              val carBenefitStartDate = getDateInTaxYear(Some(carBenefit.dateMadeAvailable))

              keyStoreService.addKeyStoreEntry(generateKeystoreActionId(taxYear, employmentSequenceNumber), KeystoreUtils.source, keystoreKey, (addFuelBenefitData)).map {
                _ =>
                  val fuelData = AddFuelBenefitConfirmationData(employment.employerName, Some(carBenefitStartDate), addFuelBenefitData.employerPayFuel.get,
                    addFuelBenefitData.dateFuelWithdrawn)

                  Ok(views.html.paye.add_fuel_benefit_review(fuelData, request.uri, taxYear, employmentSequenceNumber, user)(request))
              }
            })
        }
        case None => {
          Logger.debug(s"Unable to find employment for user ${
            user.oid
          } with sequence number $employmentSequenceNumber")
          Future.successful(BadRequest)
        }
      }
  }

  private[paye] def confirmAddFuelBenefitAction: (User, Request[_], Int, Int) => Future[SimpleResult] = AddBenefitFlow(BenefitTypes.FUEL) {
    (user: User, request: Request[_], taxYear: Int, employmentSequenceNumber: Int, taxYearData: TaxYearData) => {
      implicit val hc = HeaderCarrier(request)
      val keystoreId = generateKeystoreActionId(taxYear, employmentSequenceNumber)

      keyStoreService.getEntry[FuelBenefitDataWithGrossBenefit](keystoreId, KeystoreUtils.source, keystoreKey).flatMap {
        values =>
          val (fuelBenefitData) = values.getOrElse(throw new IllegalStateException(s"No value was returned from the keystore for AddFuelBenefit:${
            user.oid
          }:$taxYear:$employmentSequenceNumber"))

          val carBenefit = retrieveCarBenefit(taxYearData, employmentSequenceNumber)
          val updatedCarBenefit = carBenefit.copy(fuelBenefit = Some(FuelBenefit(carBenefit.startDate, 0, 0, fuelBenefitData.dateFuelWithdrawn)))

          val payeRoot = user.regimes.paye.get
          val payeAddBenefitUri = payeRoot.addBenefitLink(taxYear).getOrElse(throw new IllegalStateException(s"No link was available for adding a benefit for user with oid ${
            user.oid
          }"))

        payeRoot.version.flatMap{
          version =>
            val addBenefitsResponse = payeConnector.addBenefits(payeAddBenefitUri, version, employmentSequenceNumber, updatedCarBenefit.toBenefits)

            keyStoreService.deleteKeyStore(keystoreId, KeystoreUtils.source)

            TaxCodeResolver.currentTaxCode(payeRoot, employmentSequenceNumber, taxYear).flatMap {
              currentTaxYearCode =>
                val f1 = addBenefitsResponse.map(_.get.newTaxCode)
                val f2 = addBenefitsResponse.map(_.get.netCodedAllowance)

                for {
                  newTaxCode <- f1
                  netCodedAllowance <- f2
                } yield {
                  val benefitUpdateConfirmationData = BenefitUpdatedConfirmationData(currentTaxYearCode, newTaxCode, netCodedAllowance, startOfCurrentTaxYear, endOfCurrentTaxYear)
                  Ok(add_car_benefit_confirmation(benefitUpdateConfirmationData))
                }
          }
        }
      }
    }
  }

  private def retrieveCarBenefit(taxYearData: TaxYearData, employmentSequenceNumber: Int): CarBenefit = {
    taxYearData.findActiveCarBenefit(employmentSequenceNumber) match {
      case Some(carBenefit) => carBenefit
      case _ => throw new StaleHodDataException("No Car benefit found!") //TODO: Refine this error scenario
    }
  }

  private def getDateInTaxYear(benefitDate: Option[LocalDate]): LocalDate = {
    benefitDate match {
      case Some(suppliedDate) if suppliedDate.isAfter(startOfCurrentTaxYear) => suppliedDate
      case _ => startOfCurrentTaxYear
    }
  }

  private def findEmployment(employmentSequenceNumber: Int, payeRootData: TaxYearData) = {
    payeRootData.employments.find(_.sequenceNumber == employmentSequenceNumber)
  }

  private def generateKeystoreActionId(taxYear: Int, employmentSequenceNumber: Int) = {
    s"AddFuelBenefit:$taxYear:$employmentSequenceNumber"
  }
}

case class FuelBenefitData(employerPayFuel: Option[String], dateFuelWithdrawn: Option[LocalDate])

case class EmployerPayeFuelString(employerPayFuel: Option[String])

object FuelBenefitFormFields {
  val employerPayFuel = "employerPayFuel"
  val dateFuelWithdrawn = "dateFuelWithdrawn"
}

class StaleHodDataException(message: String, cause: Throwable) extends RuntimeException(message, cause) {
  def this(message: String) = this(message, null)
}
