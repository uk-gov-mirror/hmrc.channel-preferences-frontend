
import java.util.UUID

import org.scalatest.BeforeAndAfterEach

class ManagePaperlessPartialISpec
  extends PreferencesFrontEndServer
  with BeforeAndAfterEach
  with EmailSupport {

  "Manage Paperless partial" should {

    "return not authorised when no credentials supplied" in new TestCase {
      `/paperless/manage`(returnUrl = "http://some/other/url", returnLinkText = "Continue").get should have(status(401))
    }

    "return opted out details when no preference is set" in new TestCaseWithFrontEndAuthentication {
      private val request = `/paperless/manage`(returnUrl = "http://some/other/url", returnLinkText = "Continue").withHeaders(cookieWithNino)
      val response = request.get()
      response should have(status(200))
      response.futureValue.body should (
        include("Sign up for paperless notifications") and
        not include "You need to verify"
      )
    }
  }

  "Manage Paperless partial for pending verification" should {

    "contain pending email verification details" in new TestCaseWithFrontEndAuthentication {
      val email = s"${UUID.randomUUID().toString}@email.com"
      `/preferences/terms-and-conditions`(ggAuthHeaderWithNino).postPendingEmail(email) should have(status(201))

      val response = `/paperless/manage`(returnUrl = "http://some/other/url", returnLinkText = "Continue").withHeaders(cookieWithNino).get()
      response should have(status(200))
      response.futureValue.body should include(s"You need to verify")
    }

    "contain new email details for a subsequent change email" in new TestCaseWithFrontEndAuthentication {
      val email = s"${UUID.randomUUID().toString}@email.com"
      val newEmail = s"${UUID.randomUUID().toString}@email.com"
      `/preferences/terms-and-conditions`(ggAuthHeaderWithUtr).postPendingEmail(email) should have(status(201))
      `/preferences/terms-and-conditions`(ggAuthHeaderWithUtr).postPendingEmail(newEmail) should have(status(200))
      val response = `/paperless/manage`(returnUrl = "http://some/other/url", returnLinkText = "Continue").withHeaders(cookieWithUtr).get()
      response should have(status(200))
      checkForChangedEmailDetailsInResponse(response.futureValue.body, email, newEmail, todayDate)
    }

    "contain sign up details for a subsequent opt out" in new TestCase with TestCaseWithFrontEndAuthentication {
      val email = s"${UUID.randomUUID().toString}@email.com"
      `/preferences/terms-and-conditions`(ggAuthHeaderWithUtr).postPendingEmail(email) should have(status(201))
      `/preferences/terms-and-conditions`(ggAuthHeaderWithUtr).postOptOut should have(status(200))
      val response = `/paperless/manage`(returnUrl = "http://some/other/url", returnLinkText = "Continue").withHeaders(cookieWithUtr).get()
      response should have(status(200))
      response.futureValue.body should (
        not include email and
        include(s"Sign up for paperless notifications")
      )
    }
  }

  "Manage Paperless partial for verified user" should {

    "contain new email details for a subsequent change email" in new TestCaseWithFrontEndAuthentication {
      val email = s"${UUID.randomUUID().toString}@email.com"
      val newEmail = s"${UUID.randomUUID().toString}@email.com"
      `/preferences/terms-and-conditions`(ggAuthHeaderWithUtr).postPendingEmail(email) should have(status(201))
      `/preferences-admin/sa/individual`.verifyEmailFor(`/entity-resolver/sa/:utr`(utr.value)) should have(status(204))
      `/preferences/terms-and-conditions`(ggAuthHeaderWithUtr).postPendingEmail(newEmail) should have(status(200))
      val response = `/paperless/manage`(returnUrl = "http://some/other/url", returnLinkText = "Continue").withHeaders(cookieWithUtr).get()
      response should have(status(200))
      checkForChangedEmailDetailsInResponse(response.futureValue.body, email, newEmail, todayDate)
    }

    "contain sign up details for a subsequent opt out" in new TestCaseWithFrontEndAuthentication {
      val email = s"${UUID.randomUUID().toString}@email.com"
      `/preferences/terms-and-conditions`(ggAuthHeaderWithNino).postPendingEmail(email) should have(status(201))
      `/preferences-admin/sa/individual`.verifyEmailFor(`/entity-resolver/paye/:nino`(nino.value)) should have(status(204))
      `/preferences/terms-and-conditions`(ggAuthHeaderWithNino).postOptOut should have(status(200))
      val response = `/paperless/manage`(returnUrl = "http://some/other/url", returnLinkText = "Continue").withHeaders(cookieWithNino).get()
      response should have(status(200))
      response.futureValue.body should (
        not include email and
          include(s"Sign up for paperless notifications"))
    }
  }

  "Manage Paperless partial for a bounced verification email" should {

    "contain new email details for a subsequent change email" in new TestCaseWithFrontEndAuthentication {
      val email = s"${UUID.randomUUID().toString}@email.com"
      val newEmail = s"${UUID.randomUUID().toString}@email.com"
      `/preferences/terms-and-conditions`(ggAuthHeaderWithUtr).postPendingEmail(email) should have(status(201))
      `/preferences-admin/bounce-email`.post(email) should have(status(204))
      `/preferences/terms-and-conditions`(ggAuthHeaderWithUtr).postPendingEmail(newEmail) should have(status(200))
      val response = `/paperless/manage`(returnUrl = "http://some/other/url", returnLinkText = "Continue").withHeaders(cookieWithUtr).get()
      response should have(status(200))
      checkForChangedEmailDetailsInResponse(response.futureValue.body, email, newEmail, todayDate)
    }

    "contain sign up details for a subsequent opt out" in new TestCaseWithFrontEndAuthentication {
      val email = s"${UUID.randomUUID().toString}@email.com"
      `/preferences/terms-and-conditions`(ggAuthHeaderWithUtr).postPendingEmail(email) should have(status(201))
      `/preferences-admin/bounce-email`.post(email) should have(status(204))
      `/preferences/terms-and-conditions`(ggAuthHeaderWithUtr).postOptOut should have(status(200))
      val response = `/paperless/manage`(returnUrl = "http://some/other/url", returnLinkText = "Continue").withHeaders(cookieWithUtr).get()
      response should have(status(200))
      response.futureValue.body should (
        not include email and
          include(s"Sign up for paperless notifications"))
    }

  }

  def checkForChangedEmailDetailsInResponse(response: String, oldEmail: String, newEmail: String, currentFormattedDate: String) = {
    response should (
      include(s"You need to verify your email address.") and
        include(newEmail) and
        not include oldEmail and
        include(s"on $currentFormattedDate. Click on the link in the email to verify your email address."))
  }
}
