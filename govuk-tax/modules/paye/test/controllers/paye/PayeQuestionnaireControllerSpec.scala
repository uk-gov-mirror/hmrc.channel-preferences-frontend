package controllers.paye

import uk.gov.hmrc.common.BaseSpec
import play.api.test.Helpers._
import play.api.test.{FakeRequest, FakeApplication, WithApplication}
import models.paye.AddCar
import org.jsoup.Jsoup
import play.api.i18n.Messages

class PayeQuestionnaireControllerSpec extends PayeBaseSpec {

  private lazy val controller = new PayeQuestionnaireController

  "buildAuditEvent " should {

    "create an audit event given a paye questionnaire form data containing every field" in new WithApplication(FakeApplication()) {
      val formData = PayeQuestionnaireFormData(
        transactionId = "someTxId",
        journeyType = Some(AddCar.toString),
        wasItEasy = Some(1),
        secure = Some(2),
        comfortable = Some(3),
        easyCarUpdateDetails = Some(4),
        onlineNextTime = Some(3),
        overallSatisfaction = Some(2),
        commentForImprovements = Some("comment")
      )

      val actualAuditEvent = controller.buildAuditEvent(formData)

      val detail = Map[String, String]("wasItEasy" -> "1", "secure" -> "2", "comfortable" -> "3",
        "easyCarUpdateDetails" -> "4", "onlineNextTime" -> "3", "overallSatisfaction" -> "2", "commentForImprovements" -> "comment")
      actualAuditEvent should have(
        'auditSource("frontend"),
        'auditType("questionnaire"),
        'tags(Map("questionnaire-transactionId" -> "someTxId")),
        'detail(detail)
      )
    }

    "create an audit event given a paye questionnaire form data containing transactionId only" in new WithApplication(FakeApplication()) {
      val formData = PayeQuestionnaireFormData(
        transactionId = "someTxId",
        journeyType = Some(AddCar.toString)
      )

      val actualAuditEvent = controller.buildAuditEvent(formData)

      val detail = Map[String, String]("wasItEasy" -> "None", "secure" -> "None", "comfortable" -> "None",
        "easyCarUpdateDetails" -> "None", "onlineNextTime" -> "None", "overallSatisfaction" -> "None", "commentForImprovements" -> "None")
      actualAuditEvent should have(
        'auditSource("frontend"),
        'auditType("questionnaire"),
        'tags(Map("questionnaire-transactionId" -> "someTxId")),
        'detail(detail)
      )
    }
  }

  "submitQuestionnaireAction" should {

    "return 200 if no errors are encountered" in new WithApplication(FakeApplication()) {
      implicit val request = FakeRequest().withFormUrlEncodedBody(("transactionId", "someTxId"), ("q1", "4"), ("q3", "2"), ("q4", "4"),
        ("q5", "3"), ("q7", "Some Comments"))

      val result = controller.submitQuestionnaireAction

      status(result) shouldBe 200
    }

    "return 400 if the form has no transactionId field" in new WithApplication(FakeApplication()) {
      implicit val request = FakeRequest().withFormUrlEncodedBody(("q1", "4"), ("q3", "2"), ("q4", "4"),
        ("q5", "3"), ("q7", "Some Comments"))

      val result = controller.submitQuestionnaireAction

      status(result) shouldBe 400
    }

  }

  "forwardToConfirmationPage" should {

    implicit val request = FakeRequest()
    implicit val user = johnDensmore

    "forward to the add car benefit confirmation page if the journey type is AddCar" in new WithApplication(FakeApplication())  {
      val result = controller.forwardToConfirmationPage(
        journeyType = Some("AddCar"),
        transactionId = "txId",
        oldTaxCode = Some("oldtaxcode"),
        newTaxCode = Some("newTaxCode"),
        personalAllowance = Some(456)
      )

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.title() shouldBe Messages("paye.add_car_benefit_confirmation.title")
    }

    "forward to the add car benefit confirmation page if the journey type is AddFuel" in new WithApplication(FakeApplication())  {
      val result = controller.forwardToConfirmationPage(
        journeyType = Some("AddFuel"),
        transactionId = "txId",
        oldTaxCode = Some("oldtaxcode"),
        newTaxCode = Some("newTaxCode"),
        personalAllowance = Some(456)
      )

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.title() shouldBe Messages("paye.add_car_benefit_confirmation.title")
    }

    "forward to the remove benefit confirmation page if the journey type is RemoveCar" in new WithApplication(FakeApplication())  {
      val result = controller.forwardToConfirmationPage(
        journeyType = Some("RemoveCar"),
        transactionId = "txId",
        oldTaxCode = Some("oldtaxcode"),
        newTaxCode = Some("newTaxCode"),
        personalAllowance = Some(456)
      )

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.title() shouldBe Messages("paye.remove_benefit_confirmation.title")
    }

    "forward to the remove benefit confirmation page if the journey type is RemoveFuel" in new WithApplication(FakeApplication())  {
      val result = controller.forwardToConfirmationPage(
        journeyType = Some("RemoveFuel"),
        transactionId = "txId",
        oldTaxCode = Some("oldtaxcode"),
        newTaxCode = Some("newTaxCode"),
        personalAllowance = Some(456)
      )

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.title() shouldBe Messages("paye.remove_benefit_confirmation.title")
    }

    "forward to the remove benefit confirmation page if the journey type is RemoveCarAndFuel" in new WithApplication(FakeApplication())  {
      val result = controller.forwardToConfirmationPage(
        journeyType = Some("RemoveCarAndFuel"),
        transactionId = "txId",
        oldTaxCode = Some("oldtaxcode"),
        newTaxCode = Some("newTaxCode"),
        personalAllowance = Some(456)
      )

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.title() shouldBe Messages("paye.remove_benefit_confirmation.title")
    }


    "forward to the replace car benefit confirmation page if the journey type is RplaceCar" in new WithApplication(FakeApplication())  {
      val result = controller.forwardToConfirmationPage(
        journeyType = Some("ReplaceCar"),
        transactionId = "txId",
        oldTaxCode = Some("oldtaxcode"),
        newTaxCode = Some("newTaxCode"),
        personalAllowance = Some(456)
      )

      status(result) shouldBe 200
      val doc = Jsoup.parse(contentAsString(result))
      doc.title() shouldBe Messages("paye.replace_benefit_confirmation.title")
    }

    "forward to the paye home page if the journey type is invalid" in new WithApplication(FakeApplication()) {
      val result = controller.forwardToConfirmationPage(
        journeyType = Some("Car"),
        transactionId = "txId",
        oldTaxCode = Some("oldtaxcode"),
        newTaxCode = Some("newTaxCode"),
        personalAllowance = Some(456)
      )

      status(result) shouldBe 303
      result.header.headers("Location") shouldBe routes.PayeHomeController.home(None).url
    }

    "forward to the paye home page if the journey type is missing" in new WithApplication(FakeApplication()) {
      val result = controller.forwardToConfirmationPage(
        journeyType = None,
        transactionId = "txId",
        oldTaxCode = Some("oldtaxcode"),
        newTaxCode = Some("newTaxCode"),
        personalAllowance = Some(456)
      )

      status(result) shouldBe 303
      result.header.headers("Location") shouldBe routes.PayeHomeController.home(None).url
    }

    "forward to the paye home page if the oldTaxCode is missing" in new WithApplication(FakeApplication()) {
      val result = controller.forwardToConfirmationPage(
        journeyType = Some("AddCar"),
        transactionId = "txId",
        oldTaxCode = None,
        newTaxCode = Some("newTaxCode"),
        personalAllowance = Some(456)
      )

      status(result) shouldBe 303
      result.header.headers("Location") shouldBe routes.PayeHomeController.home(None).url
    }

    "forward to the paye home page if the newTaxCode is missing" in new WithApplication(FakeApplication()) {
      val result = controller.forwardToConfirmationPage(
        journeyType = Some("AddCar"),
        transactionId = "txId",
        oldTaxCode = Some("oldtaxcode"),
        newTaxCode = None,
        personalAllowance = Some(456)
      )

      status(result) shouldBe 303
      result.header.headers("Location") shouldBe routes.PayeHomeController.home(None).url
    }

    "forward to the paye home page if the personalAllowance is missing" in new WithApplication(FakeApplication()) {
      val result = controller.forwardToConfirmationPage(
        journeyType = Some("AddCar"),
        transactionId = "txId",
        oldTaxCode = Some("oldtaxcode"),
        newTaxCode = Some("newTaxCode"),
        personalAllowance = None
      )

      status(result) shouldBe 303
      result.header.headers("Location") shouldBe routes.PayeHomeController.home(None).url
    }
  }

}
