import java.net.URLEncoder
import java.util.UUID

import play.api.Play.current
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.api.mvc.Results.EmptyContent
import uk.gov.hmrc.crypto.{PlainText, ApplicationCrypto}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.it.{ExternalService, MicroServiceEmbeddedServer, ServiceSpec}
import uk.gov.hmrc.test.it.{BearerTokenHelper, FrontendCookieHelper}
import uk.gov.hmrc.time.DateTimeUtils
import views.sa.prefs.helpers.DateFormat
import scala.concurrent.duration._
trait TestUser {
  def userId = "SA0055"

  val password = "testing123"

  def utr = "1555369043"
}

trait PreferencesFrontEndServer extends ServiceSpec {
  protected val server = new PreferencesFrontendIntegrationServer("AccountDetailPartialISpec")

  class PreferencesFrontendIntegrationServer(override val testName: String) extends MicroServiceEmbeddedServer {
    override protected val externalServices: Seq[ExternalService] = externalServiceNames.map(ExternalService.runFromJar(_))

    override protected def startTimeout: Duration = 300.seconds
  }

  def externalServiceNames: Seq[String] = {
    Seq(
      "external-government-gateway",
      "government-gateway",
      "auth",
      "message",
      "mailgun",
      "hmrc-deskpro",
      "ca-frontend",
      "email",
      "cid",
      "preferences"
    )
  }

  class TestCase extends TestUser {

    val todayDate = DateFormat.longDateFormat(Some(DateTimeUtils.now.toLocalDate)).get.body

    def uniqueEmail = s"${UUID.randomUUID().toString}@email.com"

    def changedUniqueEmail = uniqueEmail

    def encryptAndEncode(value: String) = URLEncoder.encode(ApplicationCrypto.QueryParameterCrypto.encrypt(PlainText(value)).value, "UTF-8")

    def urlWithHostContext(url: String) = new {
      def apply(returnUrl: String = "", returnLinkText: String = "") = WS.url(resource(s"$url?returnUrl=${encryptAndEncode(returnUrl)}&returnLinkText=${encryptAndEncode(returnLinkText)}"))
    }

    val `/paperless/resend-verification-email` = urlWithHostContext("/paperless/resend-verification-email")
    val `/paperless/manage` = urlWithHostContext("/paperless/manage")

    def `/account/account-details/sa/email-reminders-status` = WS.url(resource("/account/account-details/sa/email-reminders-status"))

    val payeFormTypeBody = Json.parse(s"""{"active":true}""")

    def `/preferences/paye/individual/:nino/activations/paye`(nino: String, headers: (String, String)) = new {

      def put() = WS.url(server.externalResource("preferences", s"/preferences/paye/individual/$nino/activations/paye")).withQueryString("returnUrl" -> "/some/return/url")
        .withHeaders(headers)
        .put(payeFormTypeBody)

      val resource = WS.url(server.externalResource("preferences", s"/preferences/paye/individual/$nino/activations/paye"))
    }

    def `/preferences/sa/individual/:utr/activations`(utr: String) = new {
      def put() =
        WS.url(server.externalResource("preferences", s"/preferences/sa/individual/$utr/activations")).withQueryString("returnUrl" -> "/some/return/url").put(payeFormTypeBody)
    }

    val `/portal/preferences/sa/individual` = new {
      def postPendingEmail(utr: String, pendingEmail: String) = WS.url(server.externalResource("preferences",
        s"/portal/preferences/sa/individual/$utr/print-suppression")).post(Json.parse( s"""{"digital": true, "email":"$pendingEmail"}"""))

      def postDeEnrolling(utr: String) = WS.url(server.externalResource("preferences",
        s"/portal/preferences/sa/individual/$utr/print-suppression")).post(Json.parse(s"""{"de-enrolling": true, "reason": "Pour le-test"}"""))

      def postOptOut(utr: String) = WS.url(server.externalResource("preferences",
        s"/portal/preferences/sa/individual/$utr/print-suppression")).post(Json.parse( s"""{"digital": false}"""))

      def postLegacyOptOut(utr: String) = WS.url(server.externalResource("preferences",
        s"/preferences-admin/sa/individual/$utr/legacy-opt-out")).post(Json.parse("{}"))

      def get(utr: String) = WS.url(server.externalResource("preferences", s"/portal/preferences/sa/individual/$utr/print-suppression")).get()
      }

    val `/preferences-admin/sa/individual` = new {
      def verifyEmailFor(utr: String) = WS.url(server.externalResource("preferences",
        s"/preferences-admin/sa/individual/$utr/verify-email")).post(EmptyContent())

      def postExpireVerificationLink(utr:String) = WS.url(server.externalResource("preferences",
        s"/preferences-admin/sa/individual/$utr/expire-email-verification-link")).post(EmptyContent())

      def delete(utr: String) = WS.url(server.externalResource("preferences",
        s"/preferences-admin/sa/individual/$utr/print-suppression")).delete()

      def deleteAll() = WS.url(server.externalResource("preferences",
        "/preferences-admin/sa/individual/print-suppression")).delete()

    }

    val `/preferences-admin/sa/process-nino-determination` = new {
      def post() = WS.url(server.externalResource("preferences",
        "/preferences-admin/sa/process-nino-determination")).post(EmptyContent())
    }

    val `/preferences-admin/sa/bounce-email` = new {
      def post(emailAddress: String) = WS.url(server.externalResource("preferences",
        "/preferences-admin/sa/bounce-email")).post(Json.parse( s"""{
             |"emailAddress": "$emailAddress"
             |}""".stripMargin))
    }

    val `/preferences-admin/sa/bounce-email-inbox-full` = new {
      def post(emailAddress: String) = WS.url(server.externalResource("preferences",
        "/preferences-admin/sa/bounce-email")).post(Json.parse( s"""{
             |"emailAddress": "$emailAddress",
             |"code": 552
             |}""".stripMargin))
    }

    def `/paperless/warnings` = urlWithHostContext("/paperless/warnings")()
    def `/account/preferences/warnings` = urlWithHostContext("/account/preferences/warnings")()
  }

  trait TestCaseWithFrontEndAuthentication extends TestCase with BearerTokenHelper with FrontendCookieHelper {

    implicit val hc = HeaderCarrier()

    def authResource(path: String) = {
      server.externalResource("auth", path)
    }

    lazy val keyValues = Map(
      "authToken" -> createBearerTokenFor(SaUtr(utr)).futureValue,
      "token" -> "system-assumes-valid-token",
      "userId" -> "/auth/oid/system-assumes-valid-oid"
    )

    lazy val cookie = cookieFor(createBearerTokenFor(SaUtr(utr)).futureValue)

    def bearerTokenHeader() =
      HeaderNames.AUTHORIZATION -> createBearerTokenFor(SaUtr(utr)).futureValue

    def cookieWithNino(nino: Nino) = cookieFor(createBearerTokenFor(List(SaUtr(utr), nino)).futureValue)
  }

}

