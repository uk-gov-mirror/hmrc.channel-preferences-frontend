package controllers

import test.BaseSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.mock.MockitoSugar
import play.api.test.{ FakeRequest, WithApplication }
import microservices.MockMicroServicesForTests
import microservice.auth.AuthMicroService
import org.mockito.Mockito._
import microservice.sa.domain._
import microservice.auth.domain.UserAuthority
import play.api.test.FakeApplication
import scala.Some
import play.api.mvc.{ AnyContent, Action, Cookie }
import microservice.sa.SaMicroService
import org.joda.time.DateTime

class SaControllerSpec extends BaseSpec with ShouldMatchers with MockitoSugar with CookieEncryption {

  import play.api.test.Helpers._

  private val mockAuthMicroService = mock[AuthMicroService]

  when(mockAuthMicroService.authority("/auth/oid/gfisher")).thenReturn(
    Some(UserAuthority("someIdWeDontCareAboutHere", Map("paye" -> "/personal/paye/DF334476B", "sa" -> "/personal/sa/123456789012"), Some(new DateTime(1000L)))))

  private val mockSaMicroService = mock[SaMicroService]

  when(mockSaMicroService.root("/personal/sa/123456789012")).thenReturn(
    SaRoot(
      utr = "123456789012",
      links = Map(
        "personalDetails" -> "/personal/sa/123456789012/details")
    )
  )

  val saName = "Geoff Fisher From SA"
  when(mockSaMicroService.person("/personal/sa/123456789012/details")).thenReturn(
    Some(SaPerson(
      name = saName,
      utr = "123456789012",
      address = SaIndividualAddress(
        addressLine1 = "address line 1",
        addressLine2 = "address line 2",
        addressLine3 = "address line 3",
        addressLine4 = "address line 4",
        addressLine5 = "address line 5",
        postcode = "postcode",
        foreignCountry = "foreign country",
        additionalDeliveryInformation = "additional delivery info"
      )
    ))
  )

  private def controller = new SaController with MockMicroServicesForTests {
    override val authMicroService = mockAuthMicroService
    override val saMicroService = mockSaMicroService
  }

  "The home method" should {

    "display both the Government Gateway name and CESA/SA name for Geoff Fisher and a link to his individual SA address" in new WithApplication(FakeApplication()) {

      val ggwName = "Geoffrey From GGW"
      val result = controller.home(FakeRequest().withSession("userId" -> encrypt("/auth/oid/gfisher"), "ggwName" -> ggwName))

      status(result) should be(200)

      val content = contentAsString(result)

      content should include(saName)
      content should include(ggwName)
      content should include("My Details</a>")
      content should include("href=\"/sa/details\"")
    }

  }

  "The details page" should {
    "show the individual SA address of Geoff Fisher" in new WithApplication(FakeApplication()) {

      val content = request(controller.details)

      content should include("address line 1")
      content should include("address line 2")
      content should include("address line 3")
      content should include("address line 4")
      content should include("address line 5")
      content should include("postcode")
    }
  }

  def request(action: Action[AnyContent]): String = {
    val result = action(FakeRequest().withSession(("userId", encrypt("/auth/oid/gfisher"))))

    status(result) should be(200)

    contentAsString(result)
  }
}
