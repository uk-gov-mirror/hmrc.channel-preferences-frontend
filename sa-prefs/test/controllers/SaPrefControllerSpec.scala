package controllers

import org.scalatest.{ ShouldMatchers, WordSpec }
import play.api.test.{ FakeApplication, WithApplication }
import play.api.test.FakeRequest
import org.scalatest.mock.MockitoSugar
import org.mockito.Matchers._
import org.mockito.Mockito._
import uk.gov.hmrc.MicroServiceException

class SaPrefControllerSpec extends WordSpec with ShouldMatchers with MockitoSugar {

  import play.api.test.Helpers._

  val validToken = "asdf"
  val validReturnUrl = "http%3A%2F%2Flocalhost%3A8080%2Fportal"

  def createController = new SaPrefsController {
    override lazy val saMicroService = mock[SaMicroService]
  }

  "Preferences pages" should {
    "render an email input field" in new WithApplication(FakeApplication()) {

      val controller = createController

      val page = controller.index(validToken, validReturnUrl)(FakeRequest())
      contentAsString(page) should include("email")

    }

    "include a link to keep mail preference" in {
      val controller = createController

      val page = controller.index(validToken, validReturnUrl)(FakeRequest())
      (pending)
    }
  }

  "A post to set preferences" should {
    "redirect to a confirmation page" in {
      val controller = createController

      val page = controller.submitPrefsForm(validToken, validReturnUrl)(FakeRequest().withFormUrlEncodedBody(("email", "foo@bar.com")))

      status(page) shouldBe 303
      header("Location", page).get should include(s"/sa/print-preferences/$validToken/confirm")
    }

    "show an error if the email is invalid" in {
      val controller = createController

      val page = controller.submitPrefsForm(validToken, validReturnUrl)(FakeRequest().withFormUrlEncodedBody(("email", "invalid-email")))

      status(page) shouldBe 400
      contentAsString(page) should include("error.email")
      verify(controller.saMicroService, times(0)).savePreferences(any[String], any[Boolean], any[Option[String]])
    }

    "show an error if the email is not set" in {
      val controller = createController

      val page = controller.submitPrefsForm(validToken, validReturnUrl)(FakeRequest().withFormUrlEncodedBody(("email", "")))

      status(page) shouldBe 400
      contentAsString(page) should include("error.email")
      verify(controller.saMicroService, times(0)).savePreferences(any[String], any[Boolean], any[Option[String]])
    }

    "save the user preferences" in {
      val controller = createController

      controller.submitPrefsForm(validToken, validReturnUrl)(FakeRequest().withFormUrlEncodedBody(("email", "foo@bar.com")))
      verify(controller.saMicroService, times(1)).savePreferences("A-UTR", true, Some("foo@bar.com"))
    }

  }
}