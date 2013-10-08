package controllers.paye

import org.scalatest.mock.MockitoSugar
import controllers.common.CookieEncryption
import org.scalatest.TestData
import org.joda.time.LocalDate
import play.api.test.{WithApplication, FakeRequest}
import views.formatting.Dates
import play.api.test.Helpers._
import uk.gov.hmrc.common.microservice.paye.PayeMicroService
import org.mockito.Mockito._
import org.mockito.{ArgumentMatcher, Matchers}
import uk.gov.hmrc.common.microservice.MockMicroServicesForTests
import uk.gov.hmrc.common.microservice.paye.domain._
import org.jsoup.Jsoup
import uk.gov.hmrc.common.microservice.paye.domain.Employment._
import uk.gov.hmrc.utils.TaxYearResolver
import controllers.DateFieldsHelper
import uk.gov.hmrc.common.microservice.paye.domain.PayeRoot
import uk.gov.hmrc.common.microservice.paye.domain.Car
import uk.gov.hmrc.common.microservice.domain.User
import play.api.test.FakeApplication
import uk.gov.hmrc.common.microservice.paye.domain.TaxCode
import uk.gov.hmrc.common.microservice.paye.domain.TransactionId
import uk.gov.hmrc.microservice.txqueue.TxQueueTransaction
import uk.gov.hmrc.common.microservice.paye.domain.RevisedBenefit
import uk.gov.hmrc.common.microservice.domain.RegimeRoots
import uk.gov.hmrc.common.microservice.auth.domain.{Regimes, UserAuthority}
import java.net.URI

class RemoveBenefitControllerSpec extends PayeBaseSpec with MockitoSugar with CookieEncryption with DateFieldsHelper {

  import models.paye.BenefitTypes._

  private lazy val controller = new RemoveBenefitController with MockMicroServicesForTests

  override protected def beforeEach(testData: TestData) {
    super.beforeEach(testData)

    controller.resetAll()
  }

  val isBenefitOfType = (benefType: Int) => new ArgumentMatcher[Benefit] {
    def matches(benefit: Any) = benefit != null && benefit.asInstanceOf[Benefit].benefitType == benefType
  }

  private def setupMocksForJohnDensmore(taxCodes: Seq[TaxCode], employments: Seq[Employment], benefits: Seq[Benefit],
                                        acceptedTransactions: List[TxQueueTransaction], completedTransactions: List[TxQueueTransaction]) {
    when(controller.payeMicroService.linkedResource[Seq[TaxCode]]("/paye/AB123456C/tax-codes/2013")).thenReturn(Some(taxCodes))
    when(controller.payeMicroService.linkedResource[Seq[Employment]]("/paye/AB123456C/employments/2013")).thenReturn(Some(employments))
    when(controller.payeMicroService.linkedResource[Seq[Benefit]]("/paye/AB123456C/benefits/2013")).thenReturn(Some(benefits))
    when(controller.txQueueMicroService.transaction(Matchers.matches("^/txqueue/current-status/paye/AB123456C/ACCEPTED/.*"))).thenReturn(Some(acceptedTransactions))
    when(controller.txQueueMicroService.transaction(Matchers.matches("^/txqueue/current-status/paye/AB123456C/COMPLETED/.*"))).thenReturn(Some(completedTransactions))
  }

  "Removing FUEL benefit only" should {

    "notify the user the fuel benefit will be removed for benefit with no company name" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)
      val result = controller.benefitRemovalFormAction(johnDensmore, FakeRequest(), FUEL.toString, 2013, 2)

      val doc = Jsoup.parse(contentAsString(result))

      doc.select(".benefit-type").text shouldBe "Remove your company fuel benefit"
      doc.select(".amount").text shouldBe "£22.22"
      doc.select("label[for=agreement]").text should include("899/1212121 no longer provide me with this benefit")
      doc.select("label[for=removeCar]").text should include("I would also like to remove my car benefit.")
    }

    "not show the car checkbox when the user has no car benefit" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, Seq(fuelBenefit), List.empty, List.empty)
      val result = controller.benefitRemovalFormAction(johnDensmore, FakeRequest(), FUEL.toString, 2013, 2)

      val doc = Jsoup.parse(contentAsString(result))

      doc.select(".benefit-type").text shouldBe "Remove your company fuel benefit"
      doc.select(".amount").text shouldBe "£22.22"
      doc.select("label[for=agreement]").text should include("899/1212121 no longer provide me with this benefit")
      doc.select("label[for=removeCar]") shouldBe empty
    }

  }

  "Removing non-FUEL benefit " should {

    "not display car removal checkbox" in new WithApplication(FakeApplication()) {

      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)
      val result = controller.benefitRemovalFormAction(johnDensmore, FakeRequest(), CAR.toString, 2013, 2)

      val doc = Jsoup.parse(contentAsString(result))

      doc.select("label[for=agreement]").text should include("899/1212121 no longer provide me with a company car")
      doc.select("label[for=removeCar]").text shouldBe ""
    }
  }

  "Removing fuel benefit when there s no car" should {

    "not display car removal checkbox" in new WithApplication(FakeApplication()) {

      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, Seq(fuelBenefit), List.empty, List.empty)
      val result = controller.benefitRemovalFormAction(johnDensmore, FakeRequest(), FUEL.toString, 2013, 2)

      val doc = Jsoup.parse(contentAsString(result))

      doc.select("label[for=agreement]").text should include("899/1212121 no longer provide me with this benefit")
    }
  }

  "Removing your benefit without checking the agreement checkbox in the form" should {

    "display an error message" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val result = controller.requestBenefitRemovalAction(johnDensmore, FakeRequest().withFormUrlEncodedBody(), "31", 2013, 2)

      status(result) shouldBe 400
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".benefit-type").text should include("Remove your company car benefit")
      doc.select(".error-notification").text should include("Please confirm that you are no longer provided with this benefit")
    }

    "keep the previously entered date when redirected to the form for a car benefit" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val result = controller.requestBenefitRemovalAction(johnDensmore, FakeRequest().withFormUrlEncodedBody(buildDateFormField("withdrawDate", Some("2013","9","1")) : _*), CAR.toString, 2013, 2)

      status(result) shouldBe 400
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".benefit-type").text should include("Remove your company car benefit")

      doc.getElementById("withdrawDate.day-1").attr("selected") shouldBe "selected"
      doc.getElementById("withdrawDate.month-9").attr("selected") shouldBe "selected"
      doc.getElementById("withdrawDate.year-2013").attr("selected") shouldBe "selected"
    }

    "keep the previously entered date when redirected to the form for any benefit except car" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val result = controller.requestBenefitRemovalAction(johnDensmore, FakeRequest().withFormUrlEncodedBody(buildDateFormField("withdrawDate", Some("2013","9","1")) : _*), FUEL.toString, 2013, 2)

      status(result) shouldBe 400
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".benefit-type").text should include("Remove your company fuel benefit")

      doc.getElementById("withdrawDate.day-1").attr("selected") shouldBe "selected"
      doc.getElementById("withdrawDate.month-9").attr("selected") shouldBe "selected"
      doc.getElementById("withdrawDate.year-2013").attr("selected") shouldBe "selected"
    }
  }

  "Removing your car prior to its start date" should {
    "Not validate the view and redirect with correct error" in new WithApplication(FakeApplication()) {

      val carBenefitStartedThisYear = Benefit(benefitType = 31, taxYear = 2013, grossAmount = 321.42, employmentSequenceNumber = 2, null, null, null, null, null, null,
        car = Some(Car(Some(new LocalDate()), None, Some(new LocalDate(2012, 12, 12)), 0, 2, 124, 1, "B", BigDecimal("12343.21"))), Map.empty, Map.empty)

      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, Seq(carBenefitStartedThisYear), List.empty, List.empty)

      val withdrawDate = carBenefitStartedThisYear.car.get.dateCarMadeAvailable.get.minusDays(2)

      def requestBenefitRemovalFormSubmission(date: Option[LocalDate], agreed: Boolean, removeFuel: Boolean) =
        FakeRequest().withFormUrlEncodedBody(Seq(
          "agreement" -> agreed.toString.toLowerCase)
          ++ buildDateFormField("withdrawDate", Some(localDateToTuple(Some(withdrawDate)))) : _*)

      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(withdrawDate), true, true), "31", 2013, 2)

      verify(controller.payeMicroService, never).calculateWithdrawBenefit(_, _)

      status(result) shouldBe BAD_REQUEST

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".error-notification").text should include("Invalid date: Return date cannot be before benefit was made available")
    }
  }

  "Given a user who has car and fuel benefits, removing fuel and then separately car benefit " should {

    def requestBenefitRemovalFormSubmission(date: Option[LocalDate]) =
        FakeRequest().withFormUrlEncodedBody(Seq(
          "agreement" -> "true")
          ++ buildDateFormField("withdrawDate", Some(localDateToTuple(date))) : _*)

    "allow the user to remove fuel first without showing error" in new WithApplication(FakeApplication())  {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val fuelWithdrawDate = new LocalDate()
      val carCalculationResult = CalculationResult(Map("2013" -> BigDecimal(3.46), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(fuelBenefit, fuelWithdrawDate)).thenReturn(carCalculationResult)

      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(fuelWithdrawDate)), "29", 2013, 2)

      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      val error =  doc.select(".error-notification").text
      doc.select(".title").text should include("fuel")
      doc.select(".title").text should not include ("car")
      doc.select(".amount").text shouldBe "£18.76"
    }

    "allow the user to remove car benefit when fuel is already removed without showing error" in new WithApplication(FakeApplication())  {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List(removedFuelEmployment2Transaction), List.empty)

      val carWithdrawDate = new LocalDate()
      val carCalculationResult = CalculationResult(Map("2013" -> BigDecimal(123.46), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(carBenefit, carWithdrawDate)).thenReturn(carCalculationResult)

      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(carWithdrawDate)), "31", 2013, 2)

      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".title").text should include("car")
      doc.select(".title").text should not include ("fuel")
      doc.select(".amount").text shouldBe "£197.96"
    }
  }

  "Given a user who has car and fuel benefits, removing car benefit " should {

      "In step 1, give the user the option to remove fuel benefit on the same (or different) date as the car"  in new WithApplication(FakeApplication())  {

        setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

        val result = controller.benefitRemovalFormAction(johnDensmore, FakeRequest(), CAR.toString, 2013, 2)
        status(result) shouldBe 200

        val doc = Jsoup.parse(contentAsString(result))
        doc.select(".fuel-benefit-info") should not be empty
        doc.getElementById("fuelRadio-sameDateFuel") should not be null
        doc.getElementById("fuelRadio-differentDateFuel") should not be null
      }

    "in step 1, display error message if user has not selected the type of date for fuel removal" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val withdrawDate = new LocalDate()
      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(withdrawDate), true, None), "31", 2013, 2)

      status(result) shouldBe BAD_REQUEST

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".error-notification").text should include("select an option")

      verify(controller.payeMicroService, times(0)).calculateWithdrawBenefit(Matchers.any[Benefit], Matchers.any[LocalDate]())
    }

    "in step 1, display the calculated value for removing both fuel and car benefit from the same date" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val withdrawDate = new LocalDate()

      val carCalculationResult = CalculationResult(Map("2013" -> BigDecimal(123.46), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(carBenefit, withdrawDate)).thenReturn(carCalculationResult)

      val fuelCalculationResult = CalculationResult(Map("2013" -> BigDecimal(10.01), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(fuelBenefit, withdrawDate)).thenReturn(fuelCalculationResult)


      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(withdrawDate), true, Some("sameDateFuel")), "31", 2013, 2)

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".title").text should include("car and fuel")
      doc.select(".amount").text shouldBe "£210.17"
    }

    "in step 1, display error message if user choose different date but dont select a fuel date" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val withdrawDate = new LocalDate()
      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(withdrawDate), true, Some("differentDateFuel")), "31", 2013, 2)

      status(result) shouldBe BAD_REQUEST
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".error-notification").text should include("Please enter a date")

      verify(controller.payeMicroService, times(0)).calculateWithdrawBenefit(Matchers.any[Benefit], Matchers.any[LocalDate]())
    }

    "in step 1, display error message if user choose a fuel date that is greater than the car return date" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val withdrawDate = new LocalDate()
      val fuelDate = new LocalDate().plusDays(1);

      val carCalculationResult = CalculationResult(Map("2013" -> BigDecimal(123.46), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(carBenefit, withdrawDate)).thenReturn(carCalculationResult)

      val fuelCalculationResult = CalculationResult(Map("2013" -> BigDecimal(10.01), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(fuelBenefit, withdrawDate)).thenReturn(fuelCalculationResult)


      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(withdrawDate), true, Some("differentDateFuel"), Some(fuelDate)), "31", 2013, 2)

      status(result) shouldBe BAD_REQUEST
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".error-notification").text should include("Return date for fuel cannot be after car return date")
    }

    "in step 1, display error message if user choose a fuel date before start of tax year" in new WithApplication(FakeApplication()) {
      val benefitStartDate = new LocalDate().minusYears(3)
      val carBenefitStatedLongTimeAgo = Benefit(benefitType = 31, taxYear = 2013, grossAmount = 321.42, employmentSequenceNumber = 2, null, null, null, null, null, null,
        car = Some(Car(Some(benefitStartDate), None, Some(new LocalDate(2012, 12, 12)), 0, 2, 124, 1, "B", BigDecimal("12343.21"))), Map.empty, Map.empty)

      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, Seq(carBenefitStatedLongTimeAgo, fuelBenefit), List.empty, List.empty)

      val withdrawDate = new LocalDate()
      val fuelDate = withdrawDate.minusYears(1)

      val carCalculationResult = CalculationResult(Map("2013" -> BigDecimal(123.46), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(carBenefit, withdrawDate)).thenReturn(carCalculationResult)

      val fuelCalculationResult = CalculationResult(Map("2013" -> BigDecimal(10.01), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(fuelBenefit, withdrawDate)).thenReturn(fuelCalculationResult)

      val dateintaxyear = TaxYearResolver.startOfCurrentTaxYear
      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(withdrawDate), true, Some("differentDateFuel"), Some(fuelDate)), "31", 2013, 2)

      status(result) shouldBe BAD_REQUEST
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".error-notification").text should include("Return date cannot be in previous tax years")
    }

    "display error message if user chooses a fuel date which is malformed" in new WithApplication(FakeApplication()) {
      val benefitStartDate = new LocalDate().minusYears(3)
      val carBenefitStatedLongTimeAgo = Benefit(benefitType = 31, taxYear = 2013, grossAmount = 321.42, employmentSequenceNumber = 2, null, null, null, null, null, null,
        car = Some(Car(Some(benefitStartDate), None, Some(new LocalDate(2012, 12, 12)), 0, 2, 124, 1, "B", BigDecimal("12343.21"))), Map.empty, Map.empty)

      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, Seq(carBenefitStatedLongTimeAgo, fuelBenefit), List.empty, List.empty)

      val withdrawDate = new LocalDate()

      val requestBenefitRemovalForm = FakeRequest().withFormUrlEncodedBody(Seq("agreement" -> "true", "fuelRadio" -> "differentDateFuel")
          ++ buildDateFormField("fuelWithdrawDate", Some("aa","bb","cc"))
          ++ buildDateFormField("withdrawDate", Some(localDateToTuple(Some(withdrawDate)))) : _*)

      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalForm, "31", 2013, 2)

      status(result) shouldBe BAD_REQUEST
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".error-notification").text should include("You must specify a valid date")
    }

    "not validate the fuelWithdrawDate if user chooses a fuel date which is malformed but specifies same fuel withdrawal date as the car withdrawal date" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val carCalculationResult = CalculationResult(Map("2013" -> BigDecimal(123.46), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(Matchers.argThat(isBenefitOfType(31)), Matchers.any[LocalDate]())).thenReturn(carCalculationResult)

      val fuelCalculationResult = CalculationResult(Map("2013" -> BigDecimal(10.01), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(Matchers.argThat(isBenefitOfType(29)), Matchers.any[LocalDate]())).thenReturn(fuelCalculationResult)

      val withdrawDate = new LocalDate()
      val requestBenefitRemovalForm = FakeRequest().withFormUrlEncodedBody(Seq("agreement" -> "true", "fuelRadio" -> "sameDateFuel")
          ++ buildDateFormField("fuelWithdrawDate", Some("aa","bb","cc"))
          ++ buildDateFormField("withdrawDate", Some(localDateToTuple(Some(withdrawDate)))) : _*)
      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalForm, "31", 2013, 2)

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".title").text should include("car and fuel")
      doc.select(".amount").text shouldBe "£210.17"
    }

    "in step 1, display error message if user choose a fuel date before benefit started" in new WithApplication(FakeApplication()) {

      val benefitStartDate = new LocalDate()
      val carBenefitStartedThisYear = Benefit(benefitType = 31, taxYear = 2013, grossAmount = 321.42, employmentSequenceNumber = 2, null, null, null, null, null, null,
        car = Some(Car(Some(benefitStartDate), None, Some(new LocalDate(2012, 12, 12)), 0, 2, 124, 1, "B", BigDecimal("12343.21"))), Map.empty, Map.empty)


      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, Seq(carBenefitStartedThisYear, fuelBenefit), List.empty, List.empty)

      val withdrawDate = benefitStartDate
      val fuelDate = benefitStartDate.minusDays(1)

      val carCalculationResult = CalculationResult(Map("2013" -> BigDecimal(123.46), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(carBenefit, withdrawDate)).thenReturn(carCalculationResult)

      val fuelCalculationResult = CalculationResult(Map("2013" -> BigDecimal(10.01), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(fuelBenefit, withdrawDate)).thenReturn(fuelCalculationResult)


      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(withdrawDate), true, Some("differentDateFuel"), Some(fuelDate)), "31", 2013, 2)

      status(result) shouldBe BAD_REQUEST
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".error-notification").text should include("Return date cannot be before benefit was made available")
    }

    "in step 1, keep user choice for fuel date if a bad request redirect him to the form" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val carWithdrawDate = new LocalDate()

      val withdrawDate = new LocalDate(2013,3,9)
      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(withdrawDate), true, Some("differentDateFuel"), Some(withdrawDate), None), "31", 2013, 2)

      status(result) shouldBe BAD_REQUEST
      val doc = Jsoup.parse(contentAsString(result))

      doc.getElementById("fuelRadio-differentDateFuel").hasAttr("checked") shouldBe true
      doc.getElementById("fuelWithdrawDate.day-9").hasAttr("selected") shouldBe true
      doc.getElementById("fuelWithdrawDate.month-3").hasAttr("selected") shouldBe true
      doc.getElementById("fuelWithdrawDate.year-2013").hasAttr("selected") shouldBe true

      verify(controller.payeMicroService, times(0)).calculateWithdrawBenefit(Matchers.any[Benefit], Matchers.any[LocalDate]())
    }

    "in step 2, display the calculated value for removing fuel and car benefit on different correct values" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val carWithdrawDate = new LocalDate()
      val fuelWithdrawDate = carWithdrawDate.minusDays(1)

      val carCalculationResult = CalculationResult(Map("2013" -> BigDecimal(123.46), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(carBenefit, carWithdrawDate)).thenReturn(carCalculationResult)

      val fuelCalculationResult = CalculationResult(Map("2013" -> BigDecimal(20.01), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(fuelBenefit, fuelWithdrawDate)).thenReturn(fuelCalculationResult)


      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(carWithdrawDate), true,  Some("differentDateFuel"), Some(fuelWithdrawDate)), "31", 2013, 2)

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".title").text should include("car and fuel")
      doc.select(".amount").text shouldBe "£200.17"
    }
  }

  "The car benefit removal method" should {
    "In step 1, display page correctly for well formed request" in new WithApplication(FakeApplication()) {

      val car = Car(Some(new LocalDate(1994,10,7)), None, Some(new LocalDate(2012, 12, 12)), 0, 2, 124, 1, "C", BigDecimal("15000"))
      val specialCarBenefit = Benefit(benefitType = 31, taxYear = 2013, grossAmount = 666, employmentSequenceNumber = 3, null, null, null, null, null, null,
        car = Some(car), Map.empty, Map.empty)

      setupMocksForJohnDensmore(johnDensmoresTaxCodes, Seq(Employment(sequenceNumber = 3, startDate = new LocalDate(2013, 10, 14), endDate = None, taxDistrictNumber = "899", payeNumber = "1212121", employerName = None, primaryEmploymentType)), Seq(specialCarBenefit), List.empty, List.empty)
      val result = controller.benefitRemovalFormAction(johnDensmore, FakeRequest(), CAR.toString, 2013, 3)

      status(result) shouldBe 200
    }

    "in step 1, notify the user that the fuel benefit is going to be removed with the car benefit when removing car benefit" in new WithApplication(FakeApplication()) {

      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val result = controller.benefitRemovalFormAction(johnDensmore, FakeRequest(), "31", 2013, 2)
      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".fuel-benefit-info") should not be empty

    }

    "in step 1, not notify the user about fuel benefit if the user does not have one" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, Seq(carBenefit), List.empty, List.empty)

      val result = controller.benefitRemovalFormAction(johnDensmore, FakeRequest(), "31", 2013, 2)
      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".fuel-benefit-info") shouldBe empty

    }

    "in step 1, not notify the user about fuel benefit if the user has fuel benefit but already removed it" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val transaction: TxQueueTransaction = mock[TxQueueTransaction]
      when(controller.txQueueMicroService.transaction(Matchers.any[String])).thenReturn(Some(List(transaction)))
      when(transaction.properties).thenReturn(Map("benefitTypes" -> "29", "employmentSequenceNumber" -> "2", "taxYear" -> "2013"))

      val result = controller.benefitRemovalFormAction(johnDensmore, FakeRequest(), "31", 2013, 2)
      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".fuel-benefit-info") shouldBe empty

    }

    "in step 2, display the calculated value for removing both fuel and car benefit if the user chose to remove the car benefit" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val carCalculationResult = CalculationResult(Map("2013" -> BigDecimal(123.46), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(Matchers.argThat(isBenefitOfType(31)), Matchers.any[LocalDate]())).thenReturn(carCalculationResult)

      val fuelCalculationResult = CalculationResult(Map("2013" -> BigDecimal(10.01), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(Matchers.argThat(isBenefitOfType(29)), Matchers.any[LocalDate]())).thenReturn(fuelCalculationResult)

      val withdrawDate = new LocalDate()
      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(withdrawDate), true, Some("sameDateFuel")), "31", 2013, 2)

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".title").text should include("car and fuel")
      doc.select(".amount").text shouldBe "£210.17"
    }

    "in step 2, display the calculated value for removing car benefit only if the user do not have fuel benefit for the same employment" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, carAndFuelBenefitWithDifferentEmploymentNumbers, List.empty, List.empty)

      val carCalculationResult = CalculationResult(Map("2013" -> BigDecimal(123.46), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(Matchers.argThat(isBenefitOfType(31)), Matchers.any[LocalDate]())).thenReturn(carCalculationResult)

      val withdrawDate = new LocalDate()
      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(withdrawDate), true, Some("sameDateFuel")), "31", 2013, 2)

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".title").text should include("car")
      doc.select(".title").text should not include ("fuel")
      doc.select(".amount").text shouldBe "£197.96"
    }

    "in step 2, request removal for both fuel and car benefit when both benefits are selected and user confirms" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      when(controller.payeMicroService.removeBenefits(Matchers.any[String], Matchers.any[String](), Matchers.any[Int](), Matchers.any[Seq[RevisedBenefit]](), Matchers.any[LocalDate]())).thenReturn(Some(TransactionId("someIdForCarAndFuelRemoval")))

      val withdrawDate = new LocalDate(2013, 7, 18)
      val revisedAmounts = Map(carBenefit.benefitType.toString -> BigDecimal(210.17), fuelBenefit.benefitType.toString -> BigDecimal(14.1))
      when(controller.keyStoreMicroService.getEntry[RemoveBenefitData](johnDensmore.oid, "paye_ui", "remove_benefit")).thenReturn(Some(RemoveBenefitData(withdrawDate, revisedAmounts)))

      val result = controller.confirmBenefitRemovalAction(johnDensmore, FakeRequest(), "31,29", 2013, 2)

      val revisedBenefits = Seq(RevisedBenefit(carBenefit, BigDecimal(210.17)), RevisedBenefit(fuelBenefit, BigDecimal(14.1)))
      verify(controller.payeMicroService, times(1)).removeBenefits("/paye/AB123456C/benefits/2013/1/update", "AB123456C", 22, revisedBenefits, withdrawDate)

      status(result) shouldBe 303

      headers(result).get("Location") shouldBe Some("/benefits/31,29/confirmation/someIdForCarAndFuelRemoval")
    }

    "in step 2 redirect to benefits home page if one of the benefits being removed is already a part of running transaction" in {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val transaction: TxQueueTransaction = mock[TxQueueTransaction]
      when(controller.txQueueMicroService.transaction(Matchers.any[String])).thenReturn(Some(List(transaction)))
      when(transaction.properties).thenReturn(Map("benefitTypes" -> "31", "employmentSequenceNumber" -> "2", "taxYear" -> "2013"))

      val result = controller.confirmBenefitRemovalAction(johnDensmore, FakeRequest(), "31,29", 2013, 2)

      verifyZeroInteractions(controller.keyStoreMicroService)

      status(result) shouldBe 303

      headers(result).get("Location") shouldBe Some("/benefits")
    }

    "in step 2 redirect to benefits home page if both of the benefits being removed are already a part of running transaction" in {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val transaction: TxQueueTransaction = mock[TxQueueTransaction]
      when(controller.txQueueMicroService.transaction(Matchers.any[String])).thenReturn(Some(List(transaction)))
      when(transaction.properties).thenReturn(Map("benefitTypes" -> "29,31", "employmentSequenceNumber" -> "2", "taxYear" -> "2013"))

      val result = controller.confirmBenefitRemovalAction(johnDensmore, FakeRequest(), "31,29", 2013, 2)

      verifyZeroInteractions(controller.keyStoreMicroService)

      status(result) shouldBe 303

      headers(result).get("Location") shouldBe Some("/benefits")
    }

    "in step 2, do not redirect to benefits home page if only unrelated benefits are already a part of running transaction" in {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val transaction: TxQueueTransaction = mock[TxQueueTransaction]
      when(controller.txQueueMicroService.transaction(Matchers.any[String])).thenReturn(Some(List(transaction)))
      when(transaction.properties).thenReturn(Map("benefitTypes" -> "5,6,7", "employmentSequenceNumber" -> "2", "taxYear" -> "2013"))

      when(controller.payeMicroService.removeBenefits(Matchers.any[String], Matchers.any[String](), Matchers.any[Int](), Matchers.any[Seq[RevisedBenefit]](), Matchers.any[LocalDate]())).thenReturn(Some(TransactionId("someIdForCarAndFuelRemoval")))

      val withdrawDate = new LocalDate(2013, 7, 18)
      val revisedAmounts = Map(carBenefit.benefitType.toString -> BigDecimal(210.17), fuelBenefit.benefitType.toString -> BigDecimal(14.1))
      when(controller.keyStoreMicroService.getEntry[RemoveBenefitData](johnDensmore.oid, "paye_ui", "remove_benefit")).thenReturn(Some(RemoveBenefitData(withdrawDate, revisedAmounts)))

      val result = controller.confirmBenefitRemovalAction(johnDensmore, FakeRequest(), "31,29", 2013, 2)

      val revisedBenefits = Seq(RevisedBenefit(carBenefit, BigDecimal(210.17)), RevisedBenefit(fuelBenefit, BigDecimal(14.1)))
      verify(controller.payeMicroService, times(1)).removeBenefits("/paye/AB123456C/benefits/2013/1/update", "AB123456C", 22, revisedBenefits, withdrawDate)

      status(result) shouldBe 303

      headers(result).get("Location") shouldBe Some("/benefits/31,29/confirmation/someIdForCarAndFuelRemoval")
    }

    "in step 3, display page for confirmation of removal of both fuel and car benefit when both benefits are selected and user confirms" in new WithApplication(FakeApplication()) {

      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val transaction: TxQueueTransaction = mock[TxQueueTransaction]
      when(controller.txQueueMicroService.transaction(Matchers.eq("210"), Matchers.any[PayeRoot])).thenReturn(Some(transaction))

      val withdrawDate = new LocalDate(2013, 7, 18)
      val revisedAmounts = Map(carBenefit.benefitType.toString -> BigDecimal(210.17), fuelBenefit.benefitType.toString -> BigDecimal(14.1))

      when(controller.keyStoreMicroService.getEntry[RemoveBenefitData](johnDensmore.oid, "paye_ui", "remove_benefit")).thenReturn(Some(RemoveBenefitData(withdrawDate, revisedAmounts)))

      val result = controller.benefitRemovedAction(johnDensmore, FakeRequest(), "31,29", "210")

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".benefit-type").text should include("car and fuel")
      doc.select(".transaction-id").text should include("210")
    }

    "when confirming removal of benefits, it should return 400 for a request to remove a car benefit only (if they also have a fuel benefit)" in {

      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      when(controller.txQueueMicroService.transaction(Matchers.any[String])).thenReturn(Some(List.empty))

      val result = controller.confirmBenefitRemovalAction(johnDensmore, FakeRequest(), "31", 2013, 2)

      status(result) shouldBe BAD_REQUEST

      verifyZeroInteractions(controller.keyStoreMicroService)
      verify(controller.payeMicroService, never).removeBenefits(Matchers.any[String],Matchers.any[String],Matchers.any[Int],Matchers.any[Seq[RevisedBenefit]],Matchers.any[LocalDate])
    }

  }

  "The remove benefit method" should {

    "in step 1 display car details" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val result = controller.benefitRemovalFormAction(johnDensmore, FakeRequest(), "31", 2013, 2)
      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".benefit-type").text should include("Remove your company car benefit")
      doc.select(".date-registered").text should include("December 12, 2012")
      doc.select(".amount").text should include("£321.42")
    }

    "in step 1 display an error message when return date of car greater than 7 days" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val invalidWithdrawDate = new LocalDate().plusDays(36)
      val result = controller.requestBenefitRemovalAction(johnDensmore,
        requestBenefitRemovalFormSubmission(Some(invalidWithdrawDate), true), "31", 2013, 2)

      status(result) shouldBe 400

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".benefit-type").text should include("Remove your company car benefit")
      doc.select(".date-registered").text should include("December 12, 2012")
      doc.select(".amount").text should include("£321.42")
      doc.select(".error-notification").text should include("Invalid date: Return date cannot be greater than 7 days from today")
    }

    "in step 1 display an error message when return date of the car is in the previous tax year" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val invalidWithdrawDate = new LocalDate(1999, 2, 1)
      val result = controller.requestBenefitRemovalAction(johnDensmore,
        requestBenefitRemovalFormSubmission(Some(invalidWithdrawDate), true), "31", 2013, 2)

      status(result) shouldBe 400

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".benefit-type").text should include("Remove your company car benefit")
      doc.select(".date-registered").text should include("December 12, 2012")
      doc.select(".amount").text should include("£321.42")
      doc.select(".error-notification").text should include("Invalid date: Return date cannot be in previous tax years")
    }

    "in step 1 display an error message when return date of the car is in the next tax year" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val invalidWithdrawDate = new LocalDate(2030, 2, 1)
      val result = controller.requestBenefitRemovalAction(johnDensmore,
        requestBenefitRemovalFormSubmission(Some(invalidWithdrawDate), true), "31", 2013, 2)

      status(result) shouldBe 400
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".benefit-type").text should include("Remove your company car benefit")
      doc.select(".date-registered").text should include("December 12, 2012")
      doc.select(".amount").text should include("£321.42")
      doc.select(".error-notification").text should include("Invalid date: Return date cannot be in next tax years")
    }

    "in step 1 display an error message when return date is not set" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(None, true), "31", 2013, 2)

      status(result) shouldBe 400
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".benefit-type").text should include("Remove your company car benefit")
      doc.select(".date-registered").text should include("December 12, 2012")
      doc.select(".amount").text should include("£321.42")
      doc.select(".error-notification").text should include("Please enter a date")
    }


    "in step 1 display an error message when return date and agreement response is misformed" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val requestBenefitRemovalForm = FakeRequest().withFormUrlEncodedBody(buildDateFormField("withdrawDate", Some("A", "b", "2013")) : _*)
      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalForm, "29", 2013, 2)

      status(result) shouldBe 400
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".benefit-type").text should include("Remove your company fuel benefit")
      doc.select(".error-notification").text should include("You must specify a valid date")
      doc.select(".error-notification").text should include("Please confirm that you are no longer provided with this benefit")
    }

    "in step 2 display the calculated value" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, Seq(carBenefit), List.empty, List.empty)

      val calculationResult = CalculationResult(Map("2013" -> BigDecimal(123.46), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(Matchers.any[Benefit](), Matchers.any[LocalDate]())).thenReturn(calculationResult)

      val withdrawDate = new LocalDate()
      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(withdrawDate), true), "31", 2013, 2)

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".amount").text should include("£197.96")
    }

    "in step 2 save the withdrawDate to the keystore" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, Seq(carBenefit), List.empty, List.empty)

      val revisedAmount = BigDecimal(123.46)
      val withdrawDate = new LocalDate()

      val calculationResult = CalculationResult(Map("2013" -> revisedAmount, "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(Matchers.any[Benefit](), Matchers.any[LocalDate]())).thenReturn(calculationResult)

      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(withdrawDate), true), "31", 2013, 2)

      val revisedAmounts = Map("31" -> BigDecimal(123.46))
      verify(controller.keyStoreMicroService, times(1)).addKeyStoreEntry(johnDensmore.oid, "paye_ui", "remove_benefit", RemoveBenefitData(withdrawDate, revisedAmounts))
    }

    "in step 2 call the paye service to remove the benefit and render the success page" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      when(controller.payeMicroService.removeBenefits(Matchers.any[String], Matchers.any[String](), Matchers.any[Int](), Matchers.any[Seq[RevisedBenefit]](), Matchers.any[LocalDate]())).thenReturn(Some(TransactionId("someId")))

      val withdrawDate = new LocalDate(2013, 7, 18)
      val revisedAmounts = Map("29" -> BigDecimal(123.45))
      when(controller.keyStoreMicroService.getEntry[RemoveBenefitData](johnDensmore.oid, "paye_ui", "remove_benefit")).thenReturn(Some(RemoveBenefitData(withdrawDate, revisedAmounts)))

      val result = controller.confirmBenefitRemovalAction(johnDensmore, FakeRequest(), "29", 2013, 2)

      status(result) shouldBe 303

      headers(result).get("Location") shouldBe Some("/benefits/29/confirmation/someId")

      val revisedBenefits = Seq(RevisedBenefit(fuelBenefit, BigDecimal(123.45)))
      verify(controller.payeMicroService, times(1)).removeBenefits("/paye/AB123456C/benefits/2013/1/update", "AB123456C", 22, revisedBenefits, withdrawDate)
    }

    "When posting the benefit removal form, remove car benefit too if requested" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val carCalculationResult = CalculationResult(Map("2013" -> BigDecimal(123.46), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(Matchers.argThat(isBenefitOfType(CAR)), Matchers.any[LocalDate]())).thenReturn(carCalculationResult)

      val fuelCalculationResult = CalculationResult(Map("2013" -> BigDecimal(10.01), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(Matchers.argThat(isBenefitOfType(FUEL)), Matchers.any[LocalDate]())).thenReturn(fuelCalculationResult)

      val withdrawDate = new LocalDate()
      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(withdrawDate), true, removeCar = Some(true)), FUEL.toString, 2013, 2)

      status(result) shouldBe 200
      val response = Jsoup.parse(contentAsString(result))
      response.select(".title").text shouldBe "Remove your company fuel and car benefit"
      response.select(".amount").text shouldBe "£210.17"
    }

    "When posting the benefit removal form, don t remove car benefit if not requested" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val fuelCalculationResult = CalculationResult(Map("2013" -> BigDecimal(10.01), "2014" -> BigDecimal(0)))
      when(controller.payeMicroService.calculateWithdrawBenefit(Matchers.argThat(isBenefitOfType(FUEL)), Matchers.any[LocalDate]())).thenReturn(fuelCalculationResult)

      val withdrawDate = new LocalDate()
      val result = controller.requestBenefitRemovalAction(johnDensmore, requestBenefitRemovalFormSubmission(Some(withdrawDate), true), FUEL.toString, 2013, 2)

      status(result) shouldBe 200
      val response = Jsoup.parse(contentAsString(result))
      response.select(".title").text shouldBe "Remove your company fuel benefit"
      response.select(".amount").text shouldBe "£12.21"
    }

    "in step 3 show the transaction id only if the transaction exists" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      val transaction: TxQueueTransaction = mock[TxQueueTransaction]
      when(controller.txQueueMicroService.transaction(Matchers.eq("123"), Matchers.any[PayeRoot])).thenReturn(Some(transaction))

      val withdrawDate = new LocalDate(2013, 7, 18)
      val revisedAmounts = Map("31" -> BigDecimal(123.45))
      when(controller.keyStoreMicroService.getEntry[RemoveBenefitData](johnDensmore.oid, "paye_ui", "remove_benefit")).thenReturn(Some(RemoveBenefitData(withdrawDate, revisedAmounts)))

      val result = controller.benefitRemovedAction(johnDensmore, FakeRequest(), "31", "123")

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".transaction-id").text should include("123")

    }

    "in step 3 return 404 if the transaction does not exist" in new WithApplication(FakeApplication()) {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List.empty, List.empty)

      when(controller.txQueueMicroService.transaction(Matchers.eq("123"), Matchers.any[PayeRoot])).thenReturn(None)
      val revisedAmounts = Map("31" -> BigDecimal(555))
      when(controller.keyStoreMicroService.getEntry[RemoveBenefitData](johnDensmore.oid, "paye_ui", "remove_benefit")).thenReturn(Some(RemoveBenefitData(withdrawDate, revisedAmounts)))

      val withdrawDate = new LocalDate(2013, 7, 18)
      val result = controller.benefitRemovedAction(johnDensmore, FakeRequest().withSession("withdraw_date" -> Dates.shortDate(withdrawDate)), "31", "123")

      status(result) shouldBe 404

    }

    "return the updated benefits list page if the user has gone back in the browser and resubmitted and the benefit has already been removed" in {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List(removedCarTransaction), List.empty)

      val result = controller.benefitRemovalFormAction(johnDensmore, FakeRequest(), "31", 2013, 1)
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/benefits")
    }

    "return the benefits list page if the user modifies the url to include a benefit type that they can not remove" in {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List(removedCarTransaction), List.empty)

      val result = controller.benefitRemovalFormAction(johnDensmore, FakeRequest(), "30", 2013, 1)
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/benefits")
    }

    "return to the benefits list page if the user modifies the url to include an incorrect sequence number" in {
      setupMocksForJohnDensmore(johnDensmoresTaxCodes, johnDensmoresEmployments, johnDensmoresBenefits, List(removedCarTransaction), List.empty)

      val result = controller.benefitRemovalFormAction(johnDensmore, FakeRequest(), "31", 2013, 3)
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/benefits")
    }
  }

  "benefitRemoved" should {
    "render a view with correct elements" in new WithApplication(FakeApplication()) {

      val car = Car(None, Some(new LocalDate(2012, 12, 12)), None, BigDecimal(10), 1, 1, 1, "12000", BigDecimal("1432"))

      val payeRoot = new PayeRoot("CE927349E", 1, "Mr", "Will", None, "Shakespeare", "Will Shakespeare", "1983-01-02", Map(), Map(), Map()) {
        override def employments(taxYear: Int)(implicit payeMicroService: PayeMicroService): Seq[Employment] = {
          Seq(Employment(1, new LocalDate(), Some(new LocalDate()), "123", "123123", None, primaryEmploymentType))
        }

        override def benefits(taxYear: Int)(implicit payeMicroService: PayeMicroService): Seq[Benefit] = {
          Seq(Benefit(31, 2013, BigDecimal("3"), 1, BigDecimal("4"), BigDecimal("5"), BigDecimal("6"), BigDecimal("7"), BigDecimal("8"), "payment", Some(car), Map[String, String](), Map[String, String]()))
        }
      }

      val ua = UserAuthority(s"/personal/paye/CE927349E", Regimes(paye = Some(URI.create(s"/personal/paye/CE927349E"))), None)

      val user = User("wshakespeare", ua, RegimeRoots(Some(payeRoot), None, None, None, None), None, None)

      val request = FakeRequest().withFormUrlEncodedBody("withdrawDate" -> "2013-07-13", "agreement" -> "true")

      when(controller.payeMicroService.calculateWithdrawBenefit(Matchers.any[Benefit], Matchers.any[LocalDate])).thenReturn(CalculationResult(Map("2013" -> BigDecimal("123"))))

      val result = controller.requestBenefitRemovalAction(user, request, "31", 2013, 1)
      val doc = Jsoup.parse(contentAsString(result))
      doc.select("h2").first().text should be("Remove your company car benefit")
    }

    "Contain correct employee names" in new WithApplication(FakeApplication()) {

      val car = Car(None, None, Some(new LocalDate()), BigDecimal(10), 1, 1, 1, "12000", BigDecimal("1432"))

      val payeRoot = new PayeRoot("CE927349E", 1, "Mr", "Will", None, "Shakespeare", "Will Shakespeare", "1983-01-02", Map(), Map(), Map()) {
        override def employments(taxYear: Int)(implicit payeMicroService: PayeMicroService): Seq[Employment] = {
          Seq(Employment(1, new LocalDate(), Some(new LocalDate()), "123", "123123", Some("Sainsburys"), primaryEmploymentType))
        }

        override def benefits(taxYear: Int)(implicit payeMicroService: PayeMicroService): Seq[Benefit] = {
          Seq(Benefit(31, 2013, BigDecimal("3"), 1, BigDecimal("4"), BigDecimal("5"), BigDecimal("6"), BigDecimal("7"), BigDecimal("8"), "payment", Some(car), Map[String, String](), Map[String, String]()))
        }
      }

      val ua = UserAuthority(s"/personal/paye/CE927349E", Regimes(paye = Some(URI.create(s"/personal/paye/CE927349E"))), None)

      val user = User("wshakespeare", ua, RegimeRoots(Some(payeRoot), None, None, None, None), None, None)

      val request: play.api.mvc.Request[_] = FakeRequest().withFormUrlEncodedBody("withdrawDate" -> "2013-07-13", "agreement" -> "true")

      when(controller.payeMicroService.calculateWithdrawBenefit(Matchers.any[Benefit], Matchers.any[LocalDate])).thenReturn(CalculationResult(Map("2013" -> BigDecimal("123"))))

      val result = controller.benefitRemovalFormAction(user, request, "31", 2013, 1)
      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".checkbox").text should not include "Some("
    }

  }

        def requestBenefitRemovalFormSubmission(withdrawDate: Option[LocalDate], agreement: Boolean, fuelRadio: Option[String] = Some("sameDateFuel"), fuelWithdrawDate: Option[LocalDate] = None, removeCar : Option[Boolean] = Some(false)) =
        FakeRequest().withFormUrlEncodedBody(Seq(
          "agreement" -> agreement.toString.toLowerCase,
          "fuelRadio" -> fuelRadio.getOrElse(""),
          "removeCar" -> removeCar.getOrElse("").toString.toLowerCase)
          ++ buildDateFormField("fuelWithdrawDate", Some(localDateToTuple(fuelWithdrawDate)))
          ++ buildDateFormField("withdrawDate", Some(localDateToTuple(withdrawDate))) : _*)
}
