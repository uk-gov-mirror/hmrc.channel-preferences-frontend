package controllers.paye

import play.api.test.{FakeRequest, WithApplication}
import scala.concurrent._
import org.jsoup.Jsoup
import play.api.test.Helpers._
import uk.gov.hmrc.common.microservice.paye.domain._
import org.joda.time.LocalDate
import uk.gov.hmrc.common.microservice.paye.PayeConnector
import uk.gov.hmrc.common.microservice.txqueue.TxQueueConnector
import uk.gov.hmrc.common.microservice.auth.AuthConnector
import uk.gov.hmrc.common.microservice.audit.AuditConnector
import org.mockito.Mockito._
import FuelBenefitFormFields._
import controllers.DateFieldsHelper
import play.api.i18n.Messages
import org.mockito.{ArgumentCaptor, Matchers}
import uk.gov.hmrc.common.microservice.keystore.KeyStoreConnector
import uk.gov.hmrc.utils.TaxYearResolver
import controllers.common.actions.HeaderCarrier
import uk.gov.hmrc.common.microservice.paye.domain.Car
import play.api.test.FakeApplication
import uk.gov.hmrc.common.microservice.paye.domain.TaxCode
import uk.gov.hmrc.common.microservice.paye.domain.TransactionId
import uk.gov.hmrc.common.microservice.paye.domain.NewBenefitCalculationResponse
import uk.gov.hmrc.common.microservice.paye.domain.AddBenefitResponse
import controllers.paye.AddFuelBenefitController.FuelBenefitDataWithGrossBenefit
import org.scalatest.concurrent.ScalaFutures
import controllers.paye.validation.BenefitFlowHelper
import models.paye.RemoveCarBenefitFormData
import controllers.common.SessionKeys

class AddFuelBenefitControllerSpec extends PayeBaseSpec with DateFieldsHelper with ScalaFutures {
  import Matchers.{any, eq => is}

  private val employmentSeqNumberOne = 1
  val benefitsCaptor = ArgumentCaptor.forClass(classOf[Seq[Benefit]])

  "calling start add fuel benefit" should {
    "return 200 and show the fuel page with the employer s name" in new TestCaseIn2012 {

      setupMocksForJohnDensmore()

      when(mockKeyStoreService.getEntry[FuelBenefitDataWithGrossBenefit](generateKeystoreActionId(testTaxYear, employmentSeqNumberOne), "paye", "AddFuelBenefitForm", false)).thenReturn(None)

      val result = controller.startAddFuelBenefitAction(johnDensmore, requestWithCorrectVersion, testTaxYear, employmentSeqNumberOne)

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.select("#company-name").text shouldBe "Weyland-Yutani Corp"
      doc.select(".h2-heading").text should include("company fuel")
    }

    "return 200 and show the fuel page with the employer s name and previously populated data. EmployerPayFuelTrue and no dateWithdrawn specified" in new TestCaseIn2012 {

      setupMocksForJohnDensmore()

      val fuelBenefitData = FuelBenefitData(Some("true"), None)
      when(mockKeyStoreService.getEntry[FuelBenefitDataWithGrossBenefit](
        is(generateKeystoreActionId(testTaxYear, employmentSeqNumberOne)),
        is("paye"),
        is("AddFuelBenefitForm"),
        is(false))(any(), any())).thenReturn(Some((fuelBenefitData)))

      val result = controller.startAddFuelBenefitAction(johnDensmore, requestWithCorrectVersion, testTaxYear, employmentSeqNumberOne)

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))

      doc.select("#employerPayFuel-true").attr("checked") shouldBe "checked"
      doc.select("#employerPayFuel-date").attr("checked") shouldBe empty
      doc.select("#employerPayFuel-again").attr("checked") shouldBe empty
    }

    "return 200 and show the fuel page with the employer s name and previously populated data. EmployerPayFuelTrue and dateWithdrawn specified including a date value" in new TestCaseIn2013 {
      val currentTaxYear = TaxYearResolver.currentTaxYear

      setupMocksForJohnDensmore()

      val dateWithdrawn = new LocalDate(currentTaxYear, 5, 30)
      val fuelBenefitData = FuelBenefitData(Some("date"), Some(dateWithdrawn))

      when(mockKeyStoreService.getEntry[FuelBenefitDataWithGrossBenefit](
        is(generateKeystoreActionId(testTaxYear, employmentSeqNumberOne)),
        is("paye"),
        is("AddFuelBenefitForm"),
        is(false))(any(), any())).thenReturn(Some((fuelBenefitData)))

      val result = controller.startAddFuelBenefitAction(johnDensmore, requestWithCorrectVersion, currentTaxYear, employmentSeqNumberOne)

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))

      doc.select("#employerPayFuel-true").attr("checked") shouldBe empty
      doc.select("#employerPayFuel-date").attr("checked") shouldBe "checked"
      doc.select("#employerPayFuel-again").attr("checked") shouldBe empty

      doc.select("[id~=dateFuelWithdrawn]").select("[id~=day-30]").attr("selected") shouldBe "selected"
      doc.select("[id~=dateFuelWithdrawn]").select("[id~=month-5]").attr("selected") shouldBe "selected"
      doc.select("[id~=dateFuelWithdrawn]").select(s"[id~=year-$currentTaxYear]").attr("selected") shouldBe "selected"
    }

    "return 200 and show the fuel page with the employer s name and previously populated data. EmployerPayFuelAgain and no dateWithdrawn specified" in new TestCaseIn2012 {

      setupMocksForJohnDensmore()

      val fuelBenefitData = FuelBenefitData(Some("again"), None)

      when(mockKeyStoreService.getEntry[FuelBenefitDataWithGrossBenefit](
        is(generateKeystoreActionId(testTaxYear, employmentSeqNumberOne)),
        is("paye"),
        is("AddFuelBenefitForm"),
        is(false))(any(), any())).thenReturn(Some((fuelBenefitData)))

      val result = controller.startAddFuelBenefitAction(johnDensmore, requestWithCorrectVersion, testTaxYear, employmentSeqNumberOne)

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))

      doc.select("#employerPayFuel-true").attr("checked") shouldBe empty
      doc.select("#employerPayFuel-date").attr("checked") shouldBe empty
      doc.select("#employerPayFuel-again").attr("checked") shouldBe "checked"
    }


    "return 200 and show the add fuel benefit form with the required fields and no values filled in" in new TestCaseIn2012 {

      setupMocksForJohnDensmore()
      when(mockKeyStoreService.getEntry[FuelBenefitDataWithGrossBenefit](generateKeystoreActionId(testTaxYear, employmentSeqNumberOne), "paye", "AddFuelBenefitForm", false)).thenReturn(None)

      val result = controller.startAddFuelBenefitAction(johnDensmore, requestWithCorrectVersion, testTaxYear, employmentSeqNumberOne)

      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select("#employerPayFuel-false") shouldBe empty
      doc.select("#employerPayFuel-true") should not be empty
      doc.select("#employerPayFuel-again") should not be empty
      doc.select("#employerPayFuel-date") should not be empty
      doc.select("#employerPayFuel-date").attr("checked") shouldBe empty
    }

    "return 200 and show the page for the fuel form with default employer name message if employer name does not exist " in new TestCaseIn2012 {

      val johnDensmoresNamelessEmployments = Seq(
        Employment(sequenceNumber = employmentSeqNumberOne, startDate = new LocalDate(testTaxYear, 7, 2), endDate = Some(new LocalDate(testTaxYear, 10, 8)), taxDistrictNumber = "898", payeNumber = "9900112", employerName = None, Employment.primaryEmploymentType))

      setupMocksForJohnDensmore(employments = johnDensmoresNamelessEmployments)
      when(mockKeyStoreService.getEntry[FuelBenefitDataWithGrossBenefit](generateKeystoreActionId(testTaxYear, employmentSeqNumberOne), "paye", "AddFuelBenefitForm", false)).thenReturn(None)

      val result = controller.startAddFuelBenefitAction(johnDensmore, requestWithCorrectVersion, testTaxYear, employmentSeqNumberOne)

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.select("#company-name").text shouldBe "your employer"
    }

    "return 400 when employer for sequence number does not exist" in new TestCaseIn2012 {

      setupMocksForJohnDensmore()

      val result = controller.startAddFuelBenefitAction(johnDensmore, requestWithCorrectVersion, testTaxYear, 5)

      status(result) shouldBe 400
    }

    "return to the car benefit home page if the user already has a fuel benefit" in new TestCaseIn2012 {

      setupMocksForJohnDensmore(cars = johnDensmoresBenefitsForEmployer1)

      val result = controller.startAddFuelBenefitAction(johnDensmore, requestWithCorrectVersion, testTaxYear, employmentSeqNumberOne)
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CarBenefitHomeController.carBenefitHome().url)
    }

    "return 400 if the requested tax year is not the current tax year" in new TestCaseIn2012 {

      setupMocksForJohnDensmore()

      val result = controller.startAddFuelBenefitAction(johnDensmore, requestWithCorrectVersion, testTaxYear + 1, employmentSeqNumberOne)

      status(result) shouldBe 400
    }

    "return 400 if the employer is not the primary employer" in new TestCaseIn2012 {

      setupMocksForJohnDensmore()

      val result = controller.startAddFuelBenefitAction(johnDensmore, requestWithCorrectVersion, testTaxYear, 2)

      status(result) shouldBe 400
    }
  }


  "clicking next on the fuel benefit data entry page" should {

    "successfully store the form values in the keystore" in new TestCaseIn2012 {
      val carBenefitStartedThisYear = Benefit(31, testTaxYear, 321.42, 1, None, None, None, None, None, None, None,
        Some(Car(Some(new LocalDate(testTaxYear, 5, 12)), None, Some(new LocalDate(testTaxYear - 1, 12, 12)), Some(0), Some("diesel"), Some(124), Some(1400), None, Some(BigDecimal("12343.21")), None, None)), actions("AB123456C", testTaxYear, 1), Map.empty)

      setupMocksForJohnDensmore(cars = Seq(CarBenefit(carBenefitStartedThisYear, None)))

      val fuelBenefitValue = 1234

      val dateFuelWithdrawnFormData = new LocalDate(testTaxYear, 6, 3)
      val employerPayFuelFormData = "date"
      val request = newRequestForSaveAddFuelBenefit(employerPayFuelVal =
        Some(employerPayFuelFormData),
        dateFuelWithdrawnVal = Some((testTaxYear.toString, "6", "3")))

      val result = controller.reviewAddFuelBenefitAction(johnDensmore, request, testTaxYear, employmentSeqNumberOne)

      status(result) shouldBe 200

      val keyStoreDataCaptor = ArgumentCaptor.forClass(classOf[(FuelBenefitData)])

      verify(mockKeyStoreService).addKeyStoreEntry(
        Matchers.eq(generateKeystoreActionId(testTaxYear, employmentSeqNumberOne)),
        Matchers.eq("paye"),
        Matchers.eq("AddFuelBenefitForm"),
        keyStoreDataCaptor.capture(),
        Matchers.eq(false))(Matchers.any(), Matchers.any())

      val (fuelBenefitData) = keyStoreDataCaptor.getValue

      fuelBenefitData.dateFuelWithdrawn shouldBe Some(dateFuelWithdrawnFormData)
      fuelBenefitData.employerPayFuel shouldBe Some(employerPayFuelFormData)
    }

    "return 200 for employerpayefuel of type date with a correct date withdrawn and display some details (not including the new tax code) in a table" in new TestCaseIn2012 {
      val carBenefitStartedThisYear = Benefit(31, testTaxYear, 321.42, 1, None, None, None, None, None, None, None,
        Some(Car(Some(new LocalDate(testTaxYear, 5, 12)), None, Some(new LocalDate(testTaxYear - 1, 12, 12)), Some(0), Some("diesel"), Some(124), Some(1400), None, Some(BigDecimal("12343.21")), None, None)), actions("AB123456C", testTaxYear, 1), Map.empty)

      setupMocksForJohnDensmore(cars = Seq(CarBenefit(carBenefitStartedThisYear)))

      val dateFuelWithdrawnFormData = new LocalDate(testTaxYear, 6, 3)
      val employerPayFuelFormData = "date"
      val request = newRequestForSaveAddFuelBenefit(employerPayFuelVal = Some(employerPayFuelFormData), dateFuelWithdrawnVal = Some((testTaxYear.toString, "6", "3")))

      val result = controller.reviewAddFuelBenefitAction(johnDensmore, request, testTaxYear, employmentSeqNumberOne)

      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select("#second-heading").text should include("Check your private fuel details")
      doc.select("#private-fuel").text should include(s"3 June $testTaxYear")
      doc.select("#fuel-benefit-taxable-value") shouldBe empty

    }

    "return 200 and show start date as beginning of the tax year if carMadeAvailable is earlier" in new TestCaseIn2012 {

      setupMocksForJohnDensmore(cars = Seq(CarBenefit(carBenefitEmployer1)))


      val fuelBenefitValue = 1234
      val request = newRequestForSaveAddFuelBenefit(employerPayFuelVal = Some("true"))

      val result = controller.reviewAddFuelBenefitAction(johnDensmore, request, testTaxYear, employmentSeqNumberOne)

      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select("#second-heading").text should include("Check your private fuel details")
      doc.select("#private-fuel").text should include(s"Yes, private fuel is available when you use the car")
    }

    "not show the users recalculated tax code" in new TestCaseIn2012 {

      setupMocksForJohnDensmore(cars = Seq(CarBenefit(carBenefitEmployer1)))

      val request = newRequestForSaveAddFuelBenefit(employerPayFuelVal = Some("true"))

      val result = controller.reviewAddFuelBenefitAction(johnDensmore, request, testTaxYear, employmentSeqNumberOne)

      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select("#fuel-benefit-taxable-value") shouldBe empty
    }

    "not include the users recalculated tax code by income tax band" in new TestCaseIn2012 {
      setupMocksForJohnDensmore(cars = Seq(CarBenefit(carBenefitEmployer1)))

      val request = newRequestForSaveAddFuelBenefit(employerPayFuelVal = Some("true"))

      val result = controller.reviewAddFuelBenefitAction(johnDensmore, request, testTaxYear, employmentSeqNumberOne)

      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.text() should (not include "£1,234" and not include "20%" and not include "40%" and not include "45%")
    }

    "return to the car benefit home page if the user already has a fuel benefit" in new TestCaseIn2012 {
      setupMocksForJohnDensmore(cars = johnDensmoresBenefitsForEmployer1)

      val result = controller.reviewAddFuelBenefitAction(johnDensmore, newRequestForSaveAddFuelBenefit(), testTaxYear, employmentSeqNumberOne)
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CarBenefitHomeController.carBenefitHome().url)
    }

    "ignore invalid withdrawn date if employerpayfuel is not date" in new TestCaseIn2012 {
      setupMocksForJohnDensmore(cars = Seq(CarBenefit(carBenefitEmployer1)))

      val result = controller.reviewAddFuelBenefitAction(johnDensmore, newRequestForSaveAddFuelBenefit(employerPayFuelVal = Some("again"), dateFuelWithdrawnVal = Some(("isdufgpsiuf", "6", "3"))), testTaxYear, employmentSeqNumberOne)
      status(result) shouldBe 200
    }

    "return 400 and display error when values form data fails validation" in new TestCaseIn2012 {
      setupMocksForJohnDensmore()

      val request = newRequestForSaveAddFuelBenefit(employerPayFuelVal = Some("date"), dateFuelWithdrawnVal = Some(("jkhasgdkhsa", "05", "30")))
      val result = controller.reviewAddFuelBenefitAction(johnDensmore, request, testTaxYear, employmentSeqNumberOne)
      status(result) shouldBe 400
      val doc = Jsoup.parse(contentAsString(result))
      doc.select("#employerPayFuel-date").attr("checked") shouldBe "checked"
      doc.select("[id~=dateFuelWithdrawn]").select("[id~=day-30]").attr("selected") shouldBe "selected"

      verifyNoSaveToKeyStore()
    }

    "return 400 if the year submitted is not the current tax year" in new TestCaseIn2012 {
      setupMocksForJohnDensmore()

      val result = controller.reviewAddFuelBenefitAction(johnDensmore, newRequestForSaveAddFuelBenefit(), testTaxYear + 1, employmentSeqNumberOne)

      status(result) shouldBe 400
      verifyNoSaveToKeyStore()
    }

    "return 400 if the submitting employment number is not the primary employment" in new TestCaseIn2012 {
      setupMocksForJohnDensmore()

      val result = controller.reviewAddFuelBenefitAction(johnDensmore, newRequestForSaveAddFuelBenefit(), testTaxYear, 2)

      status(result) shouldBe 400
      verifyNoSaveToKeyStore()
    }

    "return 200 if the user selects again for the EMPLOYER PAY FUEL" in new TestCaseIn2012 {
      setupMocksForJohnDensmore(cars = Seq(CarBenefit(carBenefitEmployer1)))

      val request = newRequestForSaveAddFuelBenefit(employerPayFuelVal = Some("again"))
      val result = controller.reviewAddFuelBenefitAction(johnDensmore, request, testTaxYear, employmentSeqNumberOne)
      status(result) shouldBe 200
    }

    "return 400 if the user does not select any option for the EMPLOYER PAY FUEL question" in new TestCaseIn2012 {
      setupMocksForJohnDensmore()
      val request = newRequestForSaveAddFuelBenefit(employerPayFuelVal = None)
      val result = controller.reviewAddFuelBenefitAction(johnDensmore, request, testTaxYear, employmentSeqNumberOne)
      status(result) shouldBe 400
      val doc = Jsoup.parse(contentAsString(result))
      doc.select("#form-add-fuel-benefit  .error-notification").text should be(Messages("error.paye.answer_mandatory"))
    }

    "return 400 if the user sends an invalid value for the EMPLOYER PAY FUEL question" in new TestCaseIn2012 {
      setupMocksForJohnDensmore()
      val request = newRequestForSaveAddFuelBenefit(employerPayFuelVal = Some("hacking!"))
      val result = controller.reviewAddFuelBenefitAction(johnDensmore, request, testTaxYear, employmentSeqNumberOne)
      status(result) shouldBe 400

      verifyNoSaveToKeyStore()
    }

    "return with an error (tbd) when a car benefit is not found" in new TestCaseIn2013 {
      setupMocksForJohnDensmore()

      val request = newRequestForSaveAddFuelBenefit(employerPayFuelVal = Some("true"))

      evaluating {
        await(controller.reviewAddFuelBenefitAction(johnDensmore, request, testTaxYear, employmentSeqNumberOne))
      } should produce[StaleHodDataException]

      verifyNoSaveToKeyStore()
    }
  }

  "clicking submit on the fuel benetfit review page" should {
    "submit the corresponding keystore data to the paye service and then show the success page when successful" in new TestCaseIn2012 {
      // given
      val carBenefitStartedThisYear = Benefit(31, testTaxYear, 321.42, 1, None, None, None, None, None, None, None,
        Some(Car(Some(new LocalDate(testTaxYear, 5, 12)), None, Some(new LocalDate(testTaxYear - 1, 12, 12)), Some(0), Some("diesel"), Some(124), Some(1400), None, Some(BigDecimal("12343.21")), Some(0), None)),
        actions("AB123456C", testTaxYear, 1), Map.empty, Some(0), None)


      setupMocksForJohnDensmore(cars = Seq(CarBenefit(carBenefitStartedThisYear)), taxCodes = Seq(TaxCode(employmentSeqNumberOne, Some(1), testTaxYear, "oldTaxCode", List.empty)))



      val fuelBenefitData = FuelBenefitData(Some("true"), None)
      when(mockKeyStoreService.getEntry[FuelBenefitDataWithGrossBenefit](
        is(generateKeystoreActionId(testTaxYear, employmentSeqNumberOne)),
        is("paye"),
        is("AddFuelBenefitForm"),
        is(false))(any(), any())).thenReturn(Some((fuelBenefitData)))
      val benefitsCapture = ArgumentCaptor.forClass(classOf[Seq[Benefit]])
      val addBenefitResponse = AddBenefitResponse(TransactionId("anOid"), Some("newTaxCode"), Some(5))
      when(mockPayeConnector.addBenefits(Matchers.eq("/paye/AB123456C/benefits/2012"), Matchers.eq(johnDensmoreVersionNumber), Matchers.eq(employmentSeqNumberOne), benefitsCaptor.capture())(Matchers.any())).thenReturn(Some(addBenefitResponse))

      val resultF = controller.confirmAddFuelBenefitAction(johnDensmore, requestWithCorrectVersion, testTaxYear, employmentSeqNumberOne)

      whenReady(resultF) { result =>

        // then
        val benefitsSentToPaye = benefitsCaptor.getValue
        benefitsSentToPaye should have length 2
        val expectedFuelBenefit = Some(Benefit(29, 2012, 0, 1, None, None, None, None, None, None, None, carBenefitStartedThisYear.car, Map(), Map(), Some(0), None))

        benefitsSentToPaye.find(_.benefitType == BenefitTypes.FUEL) shouldBe expectedFuelBenefit
        benefitsSentToPaye.find(_.benefitType == BenefitTypes.CAR) shouldBe Some(carBenefitStartedThisYear)

        status(result) shouldBe 200
        val doc = Jsoup.parse(contentAsString(result))
        doc.select("#headline").text should be ("Your company car details have been changed.")
        doc.select("#old-tax-code").text shouldBe "oldTaxCode"
        doc.select("#new-tax-code").text shouldBe "newTaxCode"
        doc.select("#personal-allowance") should be (empty)
        doc.select("#start-date") should be (empty)
        doc.select("#end-date") should be (empty)
        doc.select("#epilogue").text should include ("HMRC will write to you to confirm your new tax code within 7 days.")
        doc.select("#home-page-link").text should include ("See your updated company car information")
        doc.select("a#tax-codes").text should be ("tax codes")
        doc.select("a#tax-codes").first.attr("href") should be ("https://www.gov.uk/tax-codes")
        doc.select("a#tax-codes").first.attr("target") should be ("_blank")
        doc.select("a#tax-on-company-benefits").text should be ("tax on company benefits")
        doc.select("a#tax-on-company-benefits").first.attr("href") should be ("https://www.gov.uk/tax-company-benefits")
        doc.select("a#tax-on-company-benefits").first.attr("target") should be ("_blank")
      }
    }

    "show an error if the keystore data cannot be found" in new TestCaseIn2012 {
      setupMocksForJohnDensmore(cars = Seq.empty, taxCodes = Seq(TaxCode(employmentSeqNumberOne, Some(1), testTaxYear, "oldTaxCode", List.empty)))
      when(mockKeyStoreService.getEntry[FuelBenefitDataWithGrossBenefit](generateKeystoreActionId(testTaxYear, employmentSeqNumberOne), "paye", "AddFuelBenefitForm", false)).thenReturn(None)

      val actualMessage = (evaluating {
        await(controller.confirmAddFuelBenefitAction(johnDensmore, requestWithCorrectVersion, testTaxYear, employmentSeqNumberOne))
      } should produce[IllegalStateException]).getMessage

      actualMessage shouldBe "No value was returned from the keystore for AddFuelBenefit:jdensmore:2012:1"

    }

    "show an error if the user does not have a car benefit" in new TestCaseIn2012 {
      setupMocksForJohnDensmore(cars = Seq.empty)
      val fuelBenefitData = FuelBenefitData(Some("true"), None)
      when(mockKeyStoreService.getEntry[FuelBenefitDataWithGrossBenefit](
        is(generateKeystoreActionId(testTaxYear, employmentSeqNumberOne)),
        is("paye"),
        is("AddFuelBenefitForm"),
        is(false))(any(), any())).thenReturn(Some((fuelBenefitData)))

      val addBenefitResponse = AddBenefitResponse(TransactionId("anOid"), Some("newTaxCode"), Some(5))
      when(mockPayeConnector.addBenefits(Matchers.eq("/paye/AB123456C/benefits/2012"), Matchers.eq(johnDensmoreVersionNumber), Matchers.eq(employmentSeqNumberOne), Matchers.any(classOf[Seq[Benefit]]))(Matchers.any())).thenReturn(Some(addBenefitResponse))

      val actualMessage = (evaluating {
        await(controller.confirmAddFuelBenefitAction(johnDensmore, requestWithCorrectVersion, testTaxYear, employmentSeqNumberOne))
      } should produce[StaleHodDataException]).getMessage

      actualMessage shouldBe "No Car benefit found!"

    }

    "propagates any exceptions thrown by the paye microservice" in new TestCaseIn2012 {
      val carBenefitStartedThisYear = Benefit(31, testTaxYear, 321.42, 1, None, None, None, None, None, None, None,
        Some(Car(Some(new LocalDate(testTaxYear, 5, 12)), None, Some(new LocalDate(testTaxYear - 1, 12, 12)), None, Some("diesel"), Some(124), Some(1400), None, Some(BigDecimal("12343.21")), None, None)), actions("AB123456C", testTaxYear, 1), Map.empty)
      setupMocksForJohnDensmore(cars = Seq(CarBenefit(carBenefitStartedThisYear)), taxCodes = Seq(TaxCode(employmentSeqNumberOne, Some(1), testTaxYear, "oldTaxCode", List.empty)))
      val fuelBenefitData = FuelBenefitData(Some("true"), None)

      when(mockKeyStoreService.getEntry[FuelBenefitDataWithGrossBenefit](
        is(generateKeystoreActionId(testTaxYear, employmentSeqNumberOne)),
        is("paye"),
        is("AddFuelBenefitForm"),
        is(false))(any(), any())).thenReturn(Some((fuelBenefitData)))

      val addBenefitResponse = AddBenefitResponse(TransactionId("anOid"), Some("newTaxCode"), Some(5))

      when(mockPayeConnector.addBenefits(Matchers.eq("/paye/AB123456C/benefits/2012"), Matchers.eq(johnDensmoreVersionNumber), Matchers.eq(employmentSeqNumberOne), Matchers.any(classOf[Seq[Benefit]]))(Matchers.any())).thenThrow(new RuntimeException("Timeout!"))

      val actualMessage = (evaluating {
        await(controller.confirmAddFuelBenefitAction(johnDensmore, requestWithCorrectVersion, testTaxYear, employmentSeqNumberOne))
      } should produce[RuntimeException]).getMessage

      actualMessage shouldBe "Timeout!"
    }
  }

  private def generateKeystoreActionId(taxYear: Int, employmentSequenceNumber: Int) = {
    s"AddFuelBenefit:$taxYear:$employmentSequenceNumber"
  }

  private def newRequestForSaveAddFuelBenefit(employerPayFuelVal: Option[String] = None, dateFuelWithdrawnVal: Option[(String, String, String)] = None, path: String = "") =
    FakeRequest("GET", path).withFormUrlEncodedBody(Seq(employerPayFuel -> employerPayFuelVal.getOrElse("")) ++ buildDateFormField(dateFuelWithdrawn, dateFuelWithdrawnVal): _*).
      withSession(SessionKeys.npsVersion -> johnDensmoreVersionNumber.toString)

}

class TestCase(protected val taxYear: Int = 2012) extends WithApplication(FakeApplication()) with PayeBaseSpec {

  override lazy val testTaxYear = taxYear

  val mockPayeConnector = mock[PayeConnector]
  val mockTxQueueConnector = mock[TxQueueConnector]
  val mockAuthConnector = mock[AuthConnector]
  val mockAuditConnector = mock[AuditConnector]
  val mockKeyStoreService = mock[KeyStoreConnector]

  def verifyNoSaveToKeyStore() {
    verify(mockKeyStoreService, never()).addKeyStoreEntry(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any())
  }

  def setupMocksForJohnDensmore(taxCodes: Seq[TaxCode] = johnDensmoresTaxCodes, employments: Seq[Employment] = johnDensmoresEmployments, cars: Seq[CarBenefit] = Seq.empty, versionNumber: Int = johnDensmoreVersionNumber) {

    import Matchers._

    implicit val hc = HeaderCarrier()
    val benefits = cars.map(c => CarAndFuel(c.toBenefits(0), c.toBenefits.drop(1).headOption))
    when(mockPayeConnector.linkedResource[Seq[TaxCode]](Matchers.eq(s"/paye/AB123456C/tax-codes/$taxYear"))(any(), any())).thenReturn(Some(taxCodes))

    when(mockPayeConnector.linkedResource[Seq[Employment]](Matchers.eq(s"/paye/AB123456C/employments/$taxYear"))(any(), any())).thenReturn(Some(employments))
    when(mockPayeConnector.linkedResource[Seq[CarAndFuel]](Matchers.eq(s"/paye/AB123456C/benefit-cars/$taxYear"))(any(), any())).thenReturn(Some(benefits))

    when(mockPayeConnector.version(Matchers.eq("/paye/AB123456C/version"))(any())).thenReturn(versionNumber)

    when(mockKeyStoreService.getEntry[FuelBenefitDataWithGrossBenefit](Matchers.any(),Matchers.any(),Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any())).thenReturn(None)

    when(mockKeyStoreService.addKeyStoreEntry(Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any())).
      thenReturn(Future.successful(None))
  }
}

class TestCaseIn2012 extends TestCase {
  lazy val controller = new AddFuelBenefitController(mockKeyStoreService, mockAuditConnector, mockAuthConnector)(mockPayeConnector, mockTxQueueConnector) with MockedTaxYearSupport {
    override def currentTaxYear = taxYear
  }
}

class TestCaseIn2013 extends TestCase(2013) {
  lazy val controller = new AddFuelBenefitController(mockKeyStoreService, mockAuditConnector, mockAuthConnector)(mockPayeConnector, mockTxQueueConnector) with MockedTaxYearSupport
}
