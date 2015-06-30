package controllers.sa.prefs.internal

import connectors.{PreferencesConnector, SaEmailPreference, SaPreference}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{any, eq => is}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.i18n.Messages
import play.api.libs.json.JsString
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{EventTypes, ExtendedDataEvent}
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class UpgradeRemindersControllerSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  "create an audit event with positive upgrade decision" in new UpgradeTestCase {
    val digital = true
    val event = upgradeAndCaptureAuditEvent(digital).getValue

    event.auditSource  shouldBe "preferences-frontend"
    event.auditType shouldBe EventTypes.Succeeded
    event.tags should contain ("transactionName" -> "Set Print Preference")
    event.detail \ "client" shouldBe JsString("PAYETAI")
    event.detail \ "nino" shouldBe JsString(nino.value)
    event.detail \ "utr" shouldBe JsString(utr.utr)
    event.detail \ "TandCsScope" shouldBe JsString("Generic")
    event.detail \ "TandCsVersion" shouldBe JsString("V1")
    event.detail \ "userConfirmedReadTandCs" shouldBe JsString("true")
    event.detail \ "journey" shouldBe JsString("GenericUpgrade")
    event.detail \ "digital" shouldBe JsString("true")
    event.detail \ "cohort" shouldBe JsString("TES_MVP")

  }

  "create an audit event with negative upgrade decision" in new UpgradeTestCase {
    val digital = false
    val event = upgradeAndCaptureAuditEvent(digital).getValue

    event.auditSource  shouldBe "preferences-frontend"
    event.auditType shouldBe EventTypes.Succeeded
    event.tags should contain ("transactionName" -> "Set Print Preference")
    event.detail \ "client" shouldBe JsString("PAYETAI")
    event.detail \ "nino" shouldBe JsString(nino.value)
    event.detail \ "utr" shouldBe JsString(utr.utr)
    event.detail \ "TandCsScope" shouldBe JsString("Generic")
    event.detail \ "TandCsVersion" shouldBe JsString("V1")
    event.detail \ "userConfirmedReadTandCs" shouldBe JsString("true")
    event.detail \ "journey" shouldBe JsString("GenericUpgrade")
    event.detail \ "digital" shouldBe JsString("false")
    event.detail \ "cohort" shouldBe JsString("TES_MVP")

  }

  "the validate upgrade form" should {
    "redirect to supplied url if terms and conditions are checked and digital button pressed " in new UpgradeTestCase {

      when(controller.preferencesConnector.upgradeTermsAndConditions(is(utr), is(true))(any())).thenReturn(Future.successful(true))

      val result = await(controller.validateUpgradeForm("someUrl", utr, Some(nino))(testRequest.withFormUrlEncodedBody("submitButton" -> "digital", "accept-tc" -> "true")))

      status(result) shouldBe 303
      header("Location", result).get should include("someUrl")
    }

    "return bad request if terms and conditions are not checked and digital button pressed" in new UpgradeTestCase {

      when(controller.preferencesConnector.getPreferences(is(utr), is(Some(nino)))(any())).thenReturn(Future.successful(Some(SaPreference(true, Some(email)))))

      val result = await(controller.validateUpgradeForm("someUrl", utr, Some(nino))(testRequest.withFormUrlEncodedBody("submitButton" -> "digital")))

      status(result) shouldBe 200
      bodyOf(result) should include(Messages("sa_printing_preference.accept_tc_required"))
    }

    "redirect to supplied url if non-digital button pressed " in new UpgradeTestCase {

      when(controller.preferencesConnector.upgradeTermsAndConditions(is(utr), is(false))(any())).thenReturn(Future.successful(true))

      val result = await(controller.validateUpgradeForm("someUrl", utr, Some(nino))(testRequest.withFormUrlEncodedBody("submitButton" -> "non-digital")))

      status(result) shouldBe 303
      header("Location", result).get should include("someUrl")
    }

    "redirect to supplied url when no preference found" in new UpgradeTestCase {
      when(controller.preferencesConnector.getPreferences(is(utr), is(Some(nino)))(any())).thenReturn(Future.successful(None))

      val result = await(controller.validateUpgradeForm("someUrl", utr, Some(nino))(testRequest.withFormUrlEncodedBody("submitButton" -> "digital")))

      status(result) shouldBe 303
      header("Location", result).get should include("someUrl")
    }

  }
  "the upgrade page" should {

    "redirect to supplied url when no preference found" in new UpgradeTestCase {
      when(controller.preferencesConnector.getPreferences(is(utr), is(Some(nino)))(any())).thenReturn(Future.successful(None))

      val result = await(controller.renderUpgradePageIfPreferencesAvailable(utr, Some(nino))( testRequestwithRedirectUrl ) )

      status(result) shouldBe 303
      header("Location", result).get should include("someUrl")
    }
  }

  trait UpgradeTestCase  {

    implicit val hc = HeaderCarrier()
    implicit val request = mock[Request[AnyContent]]

    val testRequest = FakeRequest()
    val testRequestwithRedirectUrl = FakeRequest(GET, "/anything?returnUrl=someUrl")

    val utr = SaUtr("testUtr")
    val nino = Nino("CE123456A")
    val emailAddress = "someone@something.com"
    val email = SaEmailPreference(emailAddress, "STATUS", false, None, None)


    val controller = new UpgradeRemindersController {
      override val preferencesConnector: PreferencesConnector = mock[PreferencesConnector]
      override val authConnector: AuthConnector = mock[AuthConnector]
      override val auditConnector: AuditConnector = mock[AuditConnector]
    }

    def upgradeAndCaptureAuditEvent(digital: Boolean):ArgumentCaptor[ExtendedDataEvent] = {
      when(controller.preferencesConnector.upgradeTermsAndConditions(is(utr), is(digital))(any())).thenReturn(Future.successful(true))
      await(controller.upgradeTermsAndConditions(utr, Some(nino), digital))
      val eventArg : ArgumentCaptor[ExtendedDataEvent] = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
      verify(controller.auditConnector).sendEvent(eventArg.capture())(any(), any())
      return  eventArg
    }
  }
}