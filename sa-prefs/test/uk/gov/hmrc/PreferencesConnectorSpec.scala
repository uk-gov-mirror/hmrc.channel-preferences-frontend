package uk.gov.hmrc

import org.scalatest.mock.MockitoSugar
import org.scalatest.{ BeforeAndAfterEach, ShouldMatchers, WordSpec }
import play.api.libs.json.{Json, JsValue}
import play.api.test.WithApplication
import org.mockito.Mockito._
import org.mockito.{Matchers, ArgumentCaptor}
import uk.gov.hmrc.Transform._
import play.api.libs.ws.Response
import play.api.test.FakeApplication
import play.api.libs.json.JsBoolean
import scala.Some
import scala.concurrent.Future
import org.scalatest.concurrent.ScalaFutures

class TestPreferencesConnector extends PreferencesConnector with MockitoSugar {

  val httpWrapper = mock[HttpWrapper]

  override protected def httpPostAndForget(uri: String, body: JsValue, headers: Map[String, String] = Map.empty) = {
    httpWrapper.post(uri, body, headers)
  }

  override protected def httpGetF[A](uri: String)(implicit m: Manifest[A]): Future[Option[A]] = {
    httpWrapper.getF(uri)
  }

  override protected def httpPostRawF(uri: String, body: JsValue, headers: Map[String, String] = Map.empty): Future[Response] = {
    httpWrapper.httpPostRawF(uri, body, headers)
  }

  class HttpWrapper {
    def getF[T](uri: String): Future[Option[T]] = Future.successful(None)

    def post[T](uri: String, body: JsValue, headers: Map[String, String]): Option[T] = None

    def httpPostRawF(uri: String, body: JsValue, headers: Map[String, String]): Future[Response] = Future.successful(mock[Response])
  }

}

class PreferencesConnectorSpec extends WordSpec with MockitoSugar with ShouldMatchers with BeforeAndAfterEach with ScalaFutures {
  //

  lazy val preferenceConnector = new TestPreferencesConnector

  override def afterEach = reset(preferenceConnector.httpWrapper)

  val utr = "2134567"

  val email = "someEmail@email.com"
  "SaMicroService" should {
    "save preferences for a user that wants email notifications" in new WithApplication(FakeApplication()) {

      preferenceConnector.savePreferences(utr, true, Some(email))

      val bodyCaptor: ArgumentCaptor[JsValue] = ArgumentCaptor.forClass(manifest.runtimeClass.asInstanceOf[Class[JsValue]])
      verify(preferenceConnector.httpWrapper).post(Matchers.eq(s"/portal/preferences/sa/individual/$utr/print-suppression"), bodyCaptor.capture(), Matchers.any[Map[String, String]])

      val body = bodyCaptor.getValue
      (body \ "digital").as[JsBoolean].value shouldBe (true)
      (body \ "email").as[String] shouldBe email
    }

    "save preferences for a user that wants paper notifications" in new WithApplication(FakeApplication()) {

      preferenceConnector.savePreferences(utr, false)

      val bodyCaptor: ArgumentCaptor[JsValue] = ArgumentCaptor.forClass(manifest.runtimeClass.asInstanceOf[Class[JsValue]])
      verify(preferenceConnector.httpWrapper).post(Matchers.eq(s"/portal/preferences/sa/individual/$utr/print-suppression"), bodyCaptor.capture(), Matchers.any[Map[String, String]])

      val body = bodyCaptor.getValue
      (body \ "digital").as[JsBoolean].value shouldBe (false)
      (body \ "email").asOpt[String] shouldBe (None)

    }

    "get preferences for a user who opted for email notification" in new WithApplication(FakeApplication()) {

      when(preferenceConnector.httpWrapper.getF[SaPreference](s"/portal/preferences/sa/individual/$utr/print-suppression")).thenReturn(Future.successful(Some(SaPreference(true, Some("someEmail@email.com")))))
      val result = preferenceConnector.getPreferences(utr).futureValue.get
      verify(preferenceConnector.httpWrapper).getF[SaPreference](s"/portal/preferences/sa/individual/$utr/print-suppression")

      result.digital shouldBe (true)
      result.email shouldBe (Some("someEmail@email.com"))
    }

    "get preferences for a user who opted for paper notification" in new WithApplication(FakeApplication()) {

      when(preferenceConnector.httpWrapper.getF[SaPreference](s"/portal/preferences/sa/individual/$utr/print-suppression")).thenReturn(Future.successful(Some(SaPreference(false))))
      val result = preferenceConnector.getPreferences(utr).futureValue.get
      verify(preferenceConnector.httpWrapper).getF[SaPreference](s"/portal/preferences/sa/individual/$utr/print-suppression")

      result.digital shouldBe (false)
      result.email shouldBe (None)
    }

    "return none for a user who has not set preferences" in new WithApplication(FakeApplication()) {
      val mockPlayResponse = mock[Response]
      when(mockPlayResponse.status).thenReturn(404)
      when(preferenceConnector.httpWrapper.getF[SaPreference](s"/portal/preferences/sa/individual/$utr/print-suppression")).thenReturn(Future.failed(new MicroServiceException("Not Found", mockPlayResponse)))
      preferenceConnector.getPreferences(utr).futureValue shouldBe None
      verify(preferenceConnector.httpWrapper).getF[SaPreference](s"/portal/preferences/sa/individual/$utr/print-suppression")
    }

  }

  "The updateEmailValidationStatus" should {
    import EmailVerificationLinkResponse._

    "return ok if updateEmailValidationStatus returns 200" in {
      val token = "someGoodToken"
      val expected = ValidateEmail(token)
      val response = mock[Response]

      when(response.status).thenReturn(200)
      when(preferenceConnector.httpWrapper.httpPostRawF(Matchers.eq("/preferences/sa/verify-email"),
                                                                  Matchers.eq(Json.parse(toRequestBody(expected))),
                                                                  Matchers.any[Map[String, String]])).thenReturn(Future.successful(response))

      val result = preferenceConnector.updateEmailValidationStatus(token)

      result.futureValue shouldBe OK
    }

    "return ok if updateEmailValidationStatus returns 204" in {
      val token = "someGoodToken"
      val expected = ValidateEmail(token)
      val response = mock[Response]

      when(response.status).thenReturn(204)
      when(preferenceConnector.httpWrapper.httpPostRawF(Matchers.eq("/preferences/sa/verify-email"),
                                                                  Matchers.eq(Json.parse(toRequestBody(expected))),
                                                                  Matchers.any[Map[String, String]])).thenReturn(Future.successful(response))

      val result = preferenceConnector.updateEmailValidationStatus(token)

      result.futureValue shouldBe OK
    }

    "return error if updateEmailValidationStatus returns 400" in {
      val token = "someGoodToken"
      val expected = ValidateEmail(token)
      val response = mock[Response]

      when(response.status).thenReturn(400)
      when(preferenceConnector.httpWrapper.httpPostRawF(Matchers.eq("/preferences/sa/verify-email"),
                                                                  Matchers.eq(Json.parse(toRequestBody(expected))),
                                                                  Matchers.any[Map[String, String]])).thenReturn(Future.successful(response))

      val result = preferenceConnector.updateEmailValidationStatus(token)

      result.futureValue shouldBe ERROR
    }

    "return error if updateEmailValidationStatus returns 404" in {
      val token = "someGoodToken"
      val expected = ValidateEmail(token)
      val response = mock[Response]

      when(response.status).thenReturn(404)
      when(preferenceConnector.httpWrapper.httpPostRawF(Matchers.eq("/preferences/sa/verify-email"),
                                                                  Matchers.eq(Json.parse(toRequestBody(expected))),
                                                                  Matchers.any[Map[String, String]])).thenReturn(Future.successful(response))

      val result = preferenceConnector.updateEmailValidationStatus(token)

      result.futureValue shouldBe ERROR
    }

    "return error if updateEmailValidationStatus returns 500" in {
      val token = "someGoodToken"
      val expected = ValidateEmail(token)
      val response = mock[Response]

      when(response.status).thenReturn(500)
      when(preferenceConnector.httpWrapper.httpPostRawF(Matchers.eq("/preferences/sa/verify-email"),
                                                                  Matchers.eq(Json.parse(toRequestBody(expected))),
                                                                  Matchers.any[Map[String, String]])).thenReturn(Future.successful(response))

      val result = preferenceConnector.updateEmailValidationStatus(token)

      result.futureValue shouldBe ERROR
    }

    "return expired if updateEmailValidationStatus returns 410" in {
      val token = "someGoodToken"
      val expected = ValidateEmail(token)
      val response = mock[Response]

      when(response.status).thenReturn(410)
      when(preferenceConnector.httpWrapper.httpPostRawF(Matchers.eq("/preferences/sa/verify-email"),
                                                                  Matchers.eq(Json.parse(toRequestBody(expected))),
                                                                  Matchers.any[Map[String, String]])).thenReturn(Future.successful(response))

      val result = preferenceConnector.updateEmailValidationStatus(token)

      result.futureValue shouldBe EXPIRED
    }
  }
}
