package controllers.paye.validation

import controllers.paye.PayeBaseSpec
import org.scalatest.mock.MockitoSugar
import play.api.data.Form
import play.api.data.Forms._
import play.api.test.{FakeApplication, WithApplication, FakeRequest}
import AddCarBenefitValidator._
import play.api.i18n.Messages
import controllers.paye.CarBenefitFormFields._
import org.joda.time.LocalDate
import uk.gov.hmrc.utils.{TaxYearResolver, DateConverter}
import controllers.DateFieldsHelper
import scala.Some
import controllers.paye.validation.AddCarBenefitValidator.CarBenefitValues
import play.api.test.FakeApplication

class AddCarBenefitValidatorSpec extends PayeBaseSpec with MockitoSugar with DateConverter with DateFieldsHelper {


  "AddCarBenefitValidator for field ENGINE CAPACITY " should {

    case class DummyModel(engineCapacity: Option[String])

    def dummyForm = {
      Form(
        mapping(
          engineCapacity -> validateEngineCapacity(getValues())
        )(DummyModel.apply)(DummyModel.unapply))
    }

    "reject a value that is not one of the options" in new WithApplication(FakeApplication()) {
      val form = bindFormWithValue(dummyForm, engineCapacity, "-123")
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, engineCapacity, "Value not accepted. Please select one of the options.")
    }
    "accept a value that is one of the options" in {
      val form = bindFormWithValue(dummyForm, engineCapacity, "2000")
      form.hasErrors shouldBe false
      form.value.get.engineCapacity shouldBe Some("2000")
    }
  }

  "AddCarBenefitValidator for fields EMPLOYER PAY FUEL & DATE FUEL WITHDRAWN" should {

    case class DummyModel(employerPayFuel: String, dateFuelWithdrawn: Option[LocalDate])

    val values = getValues()
    def dummyForm(values: CarBenefitValues) = {
      Form(
        mapping(
          employerPayFuel -> validateEmployerPayFuel(values),
          dateFuelWithdrawn -> validateDateFuelWithdrawn(values)
        )(DummyModel.apply)(DummyModel.unapply))
    }

    "reject a value that is not one of the options for the employer pay fuel field" in new WithApplication(FakeApplication()) {
      val form = bindFormWithValue(dummyForm(values), employerPayFuel, "-123")
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, employerPayFuel, "Value not accepted. Please select one of the options.")
    }

    "reject if there is no value for the employer pay fuel field" in new WithApplication(FakeApplication()) {
      val form = bindFormWithValue(dummyForm(values), employerPayFuel, "")
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, employerPayFuel, "This field is required")
    }

    "accept a value that is one of the options" in {
      val form = bindFormWithValue(dummyForm(values), employerPayFuel, "again")
      form.hasErrors shouldBe false
      form.value.get.employerPayFuel shouldBe "again"
    }

    "reject if date fuel withdrawn is not a valid date" in new WithApplication(FakeApplication()) {
      val wrongDate = buildDateFormField(dateFuelWithdrawn, Some("a", "f", ""))
      val form = dummyForm(getValues(employerPayFuelVal = Some("date"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(wrongDate: _*))
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, dateFuelWithdrawn, "You must specify a valid date")
    }

    "reject if date fuel withdrawn is incomplete" in new WithApplication(FakeApplication()) {
      val wrongDate = buildDateFormField(dateFuelWithdrawn, Some("13", "f", "2013"))
      val form = dummyForm(getValues(employerPayFuelVal = Some("date"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(wrongDate: _*))
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, dateFuelWithdrawn, "You must specify a valid date")
    }

    "reject fuel withdrawn if it is formed of numbers but not a valid date" in new WithApplication(FakeApplication()) {
      val wrongDate = buildDateFormField(dateFuelWithdrawn, Some("31", "2", TaxYearResolver.currentTaxYear.toString))
      val form = dummyForm(getValues(employerPayFuelVal = Some("date"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(wrongDate: _*))
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, dateFuelWithdrawn, "You must specify a valid date")
    }

    "accept if date fuel withdrawn is a valid date" in new WithApplication(FakeApplication()) {
      val paramsWithDate = buildDateFormField(dateFuelWithdrawn, Some(localDateToTuple(Some(new LocalDate())))) ++ Seq(employerPayFuel -> "again")
      val form = dummyForm(values).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(paramsWithDate: _*))
      form.hasErrors shouldBe false
    }

    "reject the date value for employer pays fuel if the date withdrawn field is empty" in new WithApplication(FakeApplication()) {
      val form = dummyForm(getValues(employerPayFuelVal = Some("date"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(employerPayFuel -> "date", dateFuelWithdrawn -> ""))
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, dateFuelWithdrawn, "Please specify when your employer stopped paying for fuel.")
    }

    "reject the date value for employer pays fuel if days unavailable is not blank" in new WithApplication(FakeApplication()) {
      val form = dummyForm(getValues(numberOfDaysUnavailableVal = Some("3"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(employerPayFuel -> "date", numberOfDaysUnavailable -> "3"))
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, employerPayFuel, "Unable to calculate fuel benefit when there is an interruption in car availability.Please contact call centre.")
    }

    "reject fuel withdrawn date if not in this tax year" in new WithApplication(FakeApplication()) {
      pending //TODO:should we implement?
    }
  }

  "AddCarBenefitValidator for field CO2 FIGURE & CO2 NO FIGURE " should {

    case class Co2DummyModel(co2FigureField: Option[Int], co2NoFigure: Option[Boolean])

    val values = getValues(fuelTypeVal = Some("diesel"))

    def dummyForm(values: CarBenefitValues) = {
      Form[Co2DummyModel](
        mapping(
          co2Figure -> validateCo2Figure(values),
          co2NoFigure -> validateNoCo2Figure(values)
        )(Co2DummyModel.apply)(Co2DummyModel.unapply))
    }

    "reject a negative integer" in new WithApplication(FakeApplication()) {
      val form = bindFormWithValue(dummyForm(values), co2Figure, "-123")
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, co2Figure, "You must provide a number greater than zero.")
    }

    "reject an integer with more than 3 characters" in new WithApplication(FakeApplication()) {
      val form = bindFormWithValue(dummyForm(values), co2Figure, "1000")
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, co2Figure, "You must provide a number which is 3 characters or less.")
    }

    "reject a number value which is not an integer" in new WithApplication(FakeApplication()) {
      val form = bindFormWithValue(dummyForm(values), co2Figure, "37.3")
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, co2Figure, "Please use whole numbers only, not decimals or other characters.")
    }

    "reject a value which is not an integer" in new WithApplication(FakeApplication()) {
      val form = bindFormWithValue(dummyForm(values), co2Figure, "dmdknadsfklads.(0383k378@__//")
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, co2Figure, "Please use whole numbers only, not decimals or other characters.")
    }

    "accept a valid integer" in {
      val form = dummyForm(getValues(co2FigureVal = Some("342"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(co2Figure -> "342"))
      form.hasErrors shouldBe false
      form.value.get.co2FigureField shouldBe Some(342)
    }

    "reject a NO CO2 FIGURE that is not a boolean" in new WithApplication(FakeApplication()) {
      val form = bindFormWithValue(dummyForm(values), co2NoFigure, "dmdknadsfklads.(0383k378@__//")
      form.hasErrors shouldBe true
    }
  }

  "AddCarBenefitValidator for fields REGISTERED BEFORE 98, FUEL TYPE, CO2 FIGUREs and ENGINE CAPACITY  " should {

    case class FiguresDummyModel(registeredBefore98: Boolean, fuelType: String, co2Figure: Option[Int], co2NoFigure: Option[Boolean], engineCapacity: Option[String])

    def dummyForm(values: CarBenefitValues) = {
      Form[FiguresDummyModel](
        mapping(
          registeredBefore98 -> validateRegisteredBefore98(values),
          fuelType -> validateFuelType,
          co2Figure -> validateCo2Figure(values),
          co2NoFigure -> validateNoCo2Figure(values),
          engineCapacity -> validateEngineCapacity(values)
        )(FiguresDummyModel.apply)(FiguresDummyModel.unapply))
    }

    "reject registered before 98 if fuel type is electricity and registered before 98 is true" in new WithApplication(FakeApplication()) {
      val form = dummyForm(getValues(fuelTypeVal = Some("electricity"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(registeredBefore98 -> "true", fuelType -> "electricity"))
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, registeredBefore98, "Fuel type cannot be electric or zero emissions if your car was first registered with the DVLA before 1998.")
    }

    "reject co2 figure value if fuel type is electricity and co2 figure is not blank" in new WithApplication(FakeApplication()) {
      val form = dummyForm(getValues(fuelTypeVal = Some("electricity"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(fuelType -> "electricity", co2Figure -> "123"))
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, co2Figure, "CO2 emissions must be blank if your fuel type is electric or zero emissions.")
    }

    "reject co2 no figure value if fuel type is electricity and no co2 figure is true" in new WithApplication(FakeApplication()) {
      val form = dummyForm(getValues(fuelTypeVal = Some("electricity"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(fuelType -> "electricity", co2NoFigure -> "true"))
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, co2NoFigure, "CO2 emissions must be blank if your fuel type is electric or zero emissions.")
    }

    "reject CO2 figure if Co2 no figure is selected" in new WithApplication(FakeApplication()) {
      val form = dummyForm(getValues(co2NoFigureVal = Some("true"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(co2Figure -> "123", co2NoFigure -> "true"))
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, co2Figure, "You cannot specify a CO2 emission figure and select that VCA do not have a CO2 figure.")
    }

    "reject engine capacity value if fuel type is electricity and engine capacity is not blank" in new WithApplication(FakeApplication()) {
      val form = dummyForm(getValues(fuelTypeVal = Some("electricity"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(fuelType -> "electricity", engineCapacity -> "1400"))
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, engineCapacity, "If your fuel type is electric or zero emissions, then you must not select an engine size.")
    }

    "reject engine capacity blank if fuel type is not electricity" in new WithApplication(FakeApplication()) {
      val form = dummyForm(getValues(fuelTypeVal = Some("diesel"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(fuelType -> "diesel", engineCapacity -> ""))
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, engineCapacity, "If your fuel type is not electric or zero emissions, then you must select an engine capacity. No engine size does not apply to non-electric vehicles.")
    }

    "reject co2 figure blank if co2 no figure is blank and fuel type is not electricity" in new WithApplication(FakeApplication()) {
      val form = dummyForm(getValues(fuelTypeVal = Some("diesel"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(fuelType -> "diesel", co2NoFigure -> ""))
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, co2Figure, "You must provide a CO2 emissions figure, or select that the VCA do not have one.")
    }

    "reject co2 no figure false (blank) if co2 figure is blank and fuel type is not electricity" in new WithApplication(FakeApplication()) {
      val form = dummyForm(getValues(fuelTypeVal = Some("diesel"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(fuelType -> "diesel", co2Figure -> ""))
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, co2NoFigure, "You must provide a CO2 emissions figure, or select that the VCA do not have one.")
    }

    "reject engine capacity blank if registered before 98 is true" in new WithApplication(FakeApplication()) {
      val form = dummyForm(getValues(registeredBefore98Val = Some("true"), fuelTypeVal = Some("electricity"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(registeredBefore98 -> "true", engineCapacity -> ""))
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, engineCapacity, "If your car was registered with the DVLA before 1998, then you must provide an engine capacity.")

    }

    "reject engine capacity blank if no co2 figure is true and fuel type is not electricity" in new WithApplication(FakeApplication()) {
      pending
      //TODO:this is already covered if fuel type is not electric
      val form = dummyForm(getValues(co2NoFigureVal = Some("true"), fuelTypeVal = Some("diesel"))).bindFromRequest()(FakeRequest().withFormUrlEncodedBody(fuelType -> "diesel", engineCapacity -> "", co2NoFigure -> "true"))
      form.hasErrors shouldBe true
      assertHasThisErrorMessage(form, engineCapacity, "If your car is not electric or zero emissions, and the VCA do not have a CO2 emission figure for your car, then you must select an engine capacity.")

    }
  }

  def bindFormWithValue[T](dummyForm: Form[T], field: String, value: String): Form[T] = {
    dummyForm.bindFromRequest()(FakeRequest().withFormUrlEncodedBody(field -> value))
  }

  def assertHasThisErrorMessage[T](form: Form[T], field: String, expectedErrorMessage: String) = {
    Messages(form.errors(field).head.message) shouldBe expectedErrorMessage
  }

  def getValues(fuelTypeVal: Option[String] = None, co2NoFigureVal: Option[String] = None, co2FigureVal: Option[String] = None,
                registeredBefore98Val: Option[String] = None, numberOfDaysUnavailableVal: Option[String] = None, employerPayFuelVal: Option[String] = None) = new CarBenefitValues(providedFromVal = None,
    carUnavailableVal = None,
    numberOfDaysUnavailableVal = numberOfDaysUnavailableVal,
    giveBackThisTaxYearVal = None,
    providedToVal = None,
    registeredBefore98 = registeredBefore98Val,
    employeeContributes = None,
    employerContributes = None,
    fuelType = fuelTypeVal,
    co2Figure = co2FigureVal,
    co2NoFigure = co2NoFigureVal,
    employerPayFuel = employerPayFuelVal)
}
