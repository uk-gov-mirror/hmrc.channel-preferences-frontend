package controllers.bt.testframework.request

import org.mockito.Mockito._
import controllers.bt.testframework.fixtures.NonBusinessUserFixture
import controllers.common.CookieEncryption
import java.util.UUID
import controllers.common.SessionTimeoutWrapper._
import uk.gov.hmrc.common.microservice.auth.domain.UserAuthority
import uk.gov.hmrc.common.microservice.auth.domain.Regimes
import uk.gov.hmrc.common.microservice.domain.User
import uk.gov.hmrc.common.microservice.domain.RegimeRoots
import scala.Some
import play.api.test.FakeRequest
import controllers.bt.testframework.mocks.ConnectorMocks

trait NonBusinessTaxRequest extends CookieEncryption with ConnectorMocks with NonBusinessUserFixture  {

  private val userAuthority = UserAuthority(
    id = userId,
    regimes = Regimes(),
    previouslyLoggedInAt = lastLoginTimestamp)

  implicit val user = User(
    userId = userAuthority.id,
    userAuthority = userAuthority,
    regimes = RegimeRoots())

  when(mockAuthMicroService.authority(userId)).thenReturn(Some(userAuthority))

  def request = {

    val session: Seq[(String, Option[String])] = Seq(
      "sessionId" -> Some(encrypt(s"session-${UUID.randomUUID().toString}")),
      lastRequestTimestampKey -> lastRequestTimestamp.map(_.getMillis.toString),
      "userId" -> Some(encrypt(userId)))

    val cleanSession = session.collect {
      case (paramName, Some(paramValue)) => (paramName, paramValue)
    }

    FakeRequest().withSession(cleanSession: _*)
  }
}
