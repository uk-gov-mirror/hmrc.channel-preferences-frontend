package connectors

import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.audit.http.HeaderCarrier
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class PreferencesConnectorSpec extends UnitSpec with ScalaFutures with WithFakeApplication {

  implicit val hc = new HeaderCarrier


  class TestPreferencesConnector extends PreferencesConnector {
    override def serviceUrl: String = "http://preferences.service/"

    override def http: HttpGet with HttpPost with HttpPut = ???
  }

  def preferencesConnector(returnFromDoGet: Future[HttpResponse]): TestPreferencesConnector = new TestPreferencesConnector {
    override def http = new HttpGet with HttpPost with HttpPut {
      override protected def doGet(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = returnFromDoGet

      override protected def doPostString(url: String, body: String, headers: Seq[(String, String)])(implicit hc: HeaderCarrier): Future[HttpResponse] = ???

      override protected def doFormPost(url: String, body: Map[String, Seq[String]])(implicit hc: HeaderCarrier): Future[HttpResponse] = ???

      override protected def doPost[A](url: String, body: A, headers: Seq[(String, String)])(implicit rds: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = ???

      override protected def doEmptyPost[A](url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = ???

      override protected def doPut[A](url: String, body: A)(implicit rds: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = ???
    }
  }

  "The getPreferences method" should {
    "return the preferences" in {
      val preferenceConnector = preferencesConnector(Future.successful(HttpResponse(200, Some(Json.parse(
        """
          |{
          |   "digital": true,
          |   "email": {
          |     "email": "test@mail.com",
          |     "status": "verified",
          |     "mailboxFull": false
          |   }
          |}
        """.stripMargin)))))

      val preferences = preferenceConnector.getPreferences(SaUtr("1")).futureValue

      preferences shouldBe Some(SaPreference(
        digital = true, email = Some(SaEmailPreference(
          email = "test@mail.com",
          status = "verified"))
      ))
    }

    "return None for a 404" in {
      val preferenceConnector = preferencesConnector(Future.successful(HttpResponse(404, None)))

      val preferences = preferenceConnector.getPreferences(SaUtr("1")).futureValue

      preferences shouldBe None
    }

    "return None for a 410" in {
      val preferenceConnector = preferencesConnector(Future.successful(HttpResponse(410, None)))

      val preferences = preferenceConnector.getPreferences(SaUtr("1")).futureValue

      preferences shouldBe None
    }
  }

  "The getEmailAddress method" should {
    "return None for a 404" in {
      val preferenceConnector = preferencesConnector(Future.successful(HttpResponse(404)))
      preferenceConnector.getEmailAddress(SaUtr("1")).futureValue should be (None)
    }

    "return None for a 410" in {
      val preferenceConnector = preferencesConnector(Future.successful(HttpResponse(410)))
      preferenceConnector.getEmailAddress(SaUtr("1")).futureValue should be (None)
    }

    "return None when there is not an email preference" in {
      val preferenceConnector = preferencesConnector(Future.successful(HttpResponse(200, Some(Json.parse(
        """
          |{
          |  "digital": false
          |}
        """.stripMargin)))))
      preferenceConnector.getEmailAddress(SaUtr("1")).futureValue should be (None)
    }

    "return an email address when there is an email preference" in {
      val preferenceConnector = preferencesConnector(Future.successful(HttpResponse(200, Some(Json.parse(
        """
          |{
          |  "email": {
          |    "email" : "a@b.com"
          |  }
          |}
        """.stripMargin)))))
      preferenceConnector.getEmailAddress(SaUtr("1")).futureValue should be (Some("a@b.com"))
    }
  }

  "The responseToEmailVerificationLinkStatus method" should {
    import connectors.EmailVerificationLinkResponse._
    lazy val preferenceConnector = new TestPreferencesConnector()

    "return ok if updateEmailValidationStatusUnsecured returns 200" in {
      val result = preferenceConnector.responseToEmailVerificationLinkStatus(Future.successful(HttpResponse(200)))
      result.futureValue shouldBe Ok
    }

    "return ok if updateEmailValidationStatusUnsecured returns 204" in {
      val result = preferenceConnector.responseToEmailVerificationLinkStatus(Future.successful(HttpResponse(204)))
      result.futureValue shouldBe Ok
    }

    "return error if updateEmailValidationStatusUnsecured returns 400" in {
      val result = preferenceConnector.responseToEmailVerificationLinkStatus(Future.failed(new BadRequestException("")))
      result.futureValue shouldBe Error
    }

    "return error if updateEmailValidationStatusUnsecured returns 404" in {
      val result = preferenceConnector.responseToEmailVerificationLinkStatus(Future.failed(new NotFoundException("")))
      result.futureValue shouldBe Error
    }

    "pass through the failure if updateEmailValidationStatusUnsecured returns 500" in {
      val expectedErrorResponse = Upstream5xxResponse("", 500, 500)
      val result = preferenceConnector.responseToEmailVerificationLinkStatus(Future.failed(expectedErrorResponse))

      result.failed.futureValue shouldBe expectedErrorResponse
    }

    "return expired if updateEmailValidationStatusUnsecured returns 410" in {
      val result = preferenceConnector.responseToEmailVerificationLinkStatus(Future.failed(Upstream4xxResponse("", 410, 500)))
      result.futureValue shouldBe Expired
    }

    "return wrong token if updateEmailValidationStatusUnsecured returns 409" in {
      val result = preferenceConnector.responseToEmailVerificationLinkStatus(Future.failed(Upstream4xxResponse("", 409, 500)))
      result.futureValue shouldBe WrongToken
    }
  }
}
