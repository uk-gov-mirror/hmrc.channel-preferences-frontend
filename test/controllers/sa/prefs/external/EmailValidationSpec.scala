package controllers.sa.prefs.external

import helpers.ConfigHelper
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers.{any, eq => meq}
import play.api.test.Helpers._
import play.api.test.{FakeApplication, WithApplication, FakeRequest}
import org.jsoup.Jsoup
import scala.concurrent.Future
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import connectors.{EmailVerificationLinkResponse, PreferencesConnector}

class EmailValidationSpec extends WordSpec with ShouldMatchers with MockitoSugar {

  import EmailVerificationLinkResponse._

  def createController = new EmailValidationController {
    override lazy val preferencesMicroService = mock[PreferencesConnector]
  }

  val wellFormattedToken: String = "12345678-abcd-4abc-abcd-123456789012"
  val tokenWithSomeExtraStuff: String = "12345678-abcd-4abc-abcd-123456789012423"

  implicit def hc = any[HeaderCarrier]
  implicit val request = FakeRequest()

  "verify" should {

    "call the sa micro service and update the email verification status of the user" in new WithApplication(ConfigHelper.fakeApp) {
      val controller = createController
      val token = wellFormattedToken
      when(controller.preferencesMicroService.updateEmailValidationStatusUnsecured(meq(token))).thenReturn(Future.successful(Ok))

      val response = controller.verify(token)(request)

      contentAsString(response) shouldNot include("portalHomeLink/home")
      status(response) shouldBe 200
    }

    "display an error when the sa micro service fails to update a users email verification status" in new WithApplication(ConfigHelper.fakeApp) {
      val controller = createController
      val token = wellFormattedToken
      when(controller.preferencesMicroService.updateEmailValidationStatusUnsecured(meq(token))).thenReturn(Future.successful(Error))
      val response = controller.verify(token)(request)
      contentAsString(response) shouldNot include("portalHomeLink/home")
      status(response) shouldBe 400
    }

    "display an error if the email verification token is out of date" in new WithApplication(ConfigHelper.fakeApp) {
      val controller = createController
      val token = wellFormattedToken
      when(controller.preferencesMicroService.updateEmailValidationStatusUnsecured(meq(token))).thenReturn(Future.successful(Expired))

      val response = controller.verify(token)(request)

      status(response) shouldBe 200
      val html = contentAsString(response)
      html shouldNot include("portalHomeLink/home")
      val page = Jsoup.parse(html)
      page.getElementsByTag("h1").first.text shouldBe "This link has expired"
    }

    "display an error if the email verification token is not for the email pending verification" in new WithApplication(ConfigHelper.fakeApp) {
      val controller = createController
      val token = wellFormattedToken
      when(controller.preferencesMicroService.updateEmailValidationStatusUnsecured(meq(token))).thenReturn(Future.successful(WrongToken))

      val response = controller.verify(token)(request)

      status(response) shouldBe 200
      val html = contentAsString(response)
      html shouldNot include("portalHomeLink/home")
      val page = Jsoup.parse(html)
      page.getElementsByTag("h1").first.text shouldBe "You've used a link that has now expired"
    }

    "display an error if the token is not in a valid uuid format without calling the service" in new WithApplication(ConfigHelper.fakeApp) {
      val controller = createController
      val token = "badToken"
      val response = controller.verify(token)(request)
      contentAsString(response) shouldNot include("portalHomeLink/home")
      status(response) shouldBe 400
      verify(controller.preferencesMicroService, never()).updateEmailValidationStatusUnsecured(meq(token))
    }

    "display an error if the token is not in a valid uuid format (extra characters) without calling the service" in new WithApplication(ConfigHelper.fakeApp) {
      val controller = createController
      val token = tokenWithSomeExtraStuff
      val response = controller.verify(token)(request)
      contentAsString(response) shouldNot include("portalHomeLink/home")
      status(response) shouldBe 400
      verify(controller.preferencesMicroService, never()).updateEmailValidationStatusUnsecured(meq(token))
    }

  }
}