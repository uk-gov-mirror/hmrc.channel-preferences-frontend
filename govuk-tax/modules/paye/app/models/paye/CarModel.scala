package models.paye

import org.joda.time.LocalDate
import uk.gov.hmrc.common.microservice.paye.domain.{Benefit, Car, CarAndFuel}
import uk.gov.hmrc.common.microservice.paye.domain.BenefitTypes._
import controllers.paye.FuelBenefitData

case class CarBenefitData(providedFrom: Option[LocalDate],
                          carRegistrationDate: Option[LocalDate],
                          listPrice: Option[Int],
                          employeeContributes: Option[Boolean],
                          employeeContribution: Option[Int],
                          employerContributes: Option[Boolean],
                          employerContribution: Option[Int],
                          fuelType: Option[String],
                          co2Figure: Option[Int],
                          co2NoFigure: Option[Boolean],
                          engineCapacity: Option[String],
                          employerPayFuel: Option[String],
                          dateFuelWithdrawn: Option[LocalDate])

case class CarBenefitDataAndCalculations(carBenefitData : CarBenefitData, carBenefitValue: Int, fuelBenefitValue: Option[Int], forecastCarBenefitValue: Option[Int], forecastFuelBenefitValue: Option[Int])

object CarAndFuelBuilder {
  def apply(carBenefitDataAndCalculations: CarBenefitDataAndCalculations, taxYear: Int, employmentSequenceNumber: Int): CarAndFuel = {
    val carBenefitData = carBenefitDataAndCalculations.carBenefitData
    val car = createCar(carBenefitData)

    val carBenefit = createBenefit(CAR, None, taxYear, employmentSequenceNumber, Some(car), carBenefitDataAndCalculations.carBenefitValue, carBenefitDataAndCalculations.forecastCarBenefitValue)

    val fuelBenefit = carBenefitData.employerPayFuel match {
      //benefitType: Int, withdrawnDate: Option[LocalDate], taxYear: Int, employmentSeqNumber: Int, car: Option[Car], grossBenefitAmount : Int
      case Some(data) if data == "true" || data == "again" => Some(createBenefit(benefitType = 29, withdrawnDate = None, taxYear = taxYear,
        employmentSeqNumber =  employmentSequenceNumber, car = Some(car), grossBenefitAmount = carBenefitDataAndCalculations.fuelBenefitValue.get, carBenefitDataAndCalculations.forecastFuelBenefitValue))
      case Some("date") => Some(createBenefit(benefitType = 29, withdrawnDate = carBenefitData.dateFuelWithdrawn, taxYear = taxYear,
        employmentSeqNumber =  employmentSequenceNumber, car = Some(car), grossBenefitAmount = carBenefitDataAndCalculations.fuelBenefitValue.get, carBenefitDataAndCalculations.forecastFuelBenefitValue))
      case _ => None
    }
    new CarAndFuel(carBenefit, fuelBenefit)
  }

  def apply(addFuelBenefit: FuelBenefitData, taxableBenefit: Int, carBenefit: Benefit, taxYear: Int, employmentSequenceNumber: Int, forecastFuelBenefitValue: Option[Int]) : CarAndFuel  = {
    val car = carBenefit.car
    val fuelBenefit = addFuelBenefit.employerPayFuel match {

      case Some("true" | "again") => Some(createBenefit(FUEL, carBenefit.dateWithdrawn, taxYear, employmentSequenceNumber, car, taxableBenefit, forecastFuelBenefitValue))

      case Some("date") => Some(createBenefit(FUEL, addFuelBenefit.dateFuelWithdrawn, taxYear, employmentSequenceNumber, car, taxableBenefit, forecastFuelBenefitValue))

      case _ => None
    }
    new CarAndFuel(carBenefit, fuelBenefit)
  }


  private def createCar(carBenefitData: CarBenefitData) = {
    Car(dateCarMadeAvailable = carBenefitData.providedFrom,
      dateCarWithdrawn = None,
      dateCarRegistered = carBenefitData.carRegistrationDate,
      employeeCapitalContribution = carBenefitData.employeeContribution.map(BigDecimal(_)),
      fuelType = carBenefitData.fuelType,
      co2Emissions = carBenefitData.co2Figure,
      engineSize = engineSize(carBenefitData.engineCapacity),
      mileageBand = None,
      carValue = carBenefitData.listPrice.map(BigDecimal(_)),
      employeePayments = carBenefitData.employerContribution.map(BigDecimal(_)),
      daysUnavailable = None)
  }

  private def engineSize(engineCapacity: Option[String]) : Option[Int] = {
    engineCapacity match {
      // TODO: Investigate why keystore is returning a string value 'none' instead of an Option None value
      case Some(engine) if engine != EngineCapacity.NOT_APPLICABLE => Some(engine.toInt)
      case _ => None
    }
  }

  private def createBenefit(benefitType: Int, withdrawnDate: Option[LocalDate], taxYear: Int, employmentSeqNumber: Int, car: Option[Car], grossBenefitAmount : Int, forecastBenefitAmount: Option[Int]) = {
    Benefit(benefitType = benefitType,
      taxYear = taxYear,
      grossAmount = grossBenefitAmount,
      employmentSequenceNumber = employmentSeqNumber,
      costAmount = None,
      amountMadeGood = None,
      cashEquivalent = None,
      expensesIncurred = None,
      amountOfRelief = None,
      paymentOrBenefitDescription = None,
      dateWithdrawn = withdrawnDate,
      car = car,
      actions = Map.empty[String, String],
      calculations = Map.empty[String, String],
      forecastAmount = forecastBenefitAmount)
  }
}