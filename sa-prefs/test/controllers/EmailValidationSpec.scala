package controllers

import org.scalatest._
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.PreferencesMicroService
import org.mockito.Mockito._
import play.api.test.Helpers._
import play.api.test.{FakeApplication, WithApplication, FakeRequest}
import controllers.service.FrontEndConfig
import play.GlobalSettings

class EmailValidationSpec extends WordSpec with ShouldMatchers with MockitoSugar {

   val additionalConfig = Map("sa-prefs.Test.portal.destinationRoot" -> "portalHomeLink", "sa-prefs.Test.portal.destinationPath.home" -> "/home")

   def createController = new EmailValidation {
     override lazy val preferencesMicroService = mock[PreferencesMicroService]
   }

  "verify" should {

    "call the sa micro service and update the email verification status of the user" in new WithApplication(FakeApplication(additionalConfiguration = additionalConfig)) {
      val controller = createController
      val token = "someToken"
      when(controller.preferencesMicroService.updateEmailValidationStatus(token)).thenReturn(true)
      val response = controller.verify(token)(FakeRequest())
      contentAsString(response) should include("portalHomeLink/home")
      status(response) shouldBe 200
      verify(controller.preferencesMicroService).updateEmailValidationStatus(token)
    }

    "display an error when the sa micro service fails to update a users email verification status" in new WithApplication(FakeApplication(additionalConfiguration = additionalConfig)) {
      val controller = createController
      val token = "someToken"
      when(controller.preferencesMicroService.updateEmailValidationStatus(token)).thenReturn(false)
      val response = controller.verify(token)(FakeRequest())
      contentAsString(response) should include("portalHomeLink/home")
      status(response) shouldBe 400
      verify(controller.preferencesMicroService).updateEmailValidationStatus(token)
    }
  }
}