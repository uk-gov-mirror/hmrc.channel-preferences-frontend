package config

import java.net.ConnectException

import play.mvc.Http.Status._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{Upstream5xxResponse, Upstream4xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

class ServicesCircuitBreakerSpec extends UnitSpec {

  "ServicesCircuitBreaker" should {
    "count BAD_REQUEST Upstream exception" in new TestCase {
      val exception = new Upstream4xxResponse(
        message = "",
        upstreamResponseCode = BAD_REQUEST,
        reportAs = BAD_REQUEST,
        headers = Map.empty
      )
      circuitBreaker.breakOnExceptionDelegate(exception) should be (true)
    }

    "doesn't count any Upstream4xx exception different than BAD_REQUEST" in new TestCase {
      val exception = new Upstream4xxResponse(
        message = "",
        upstreamResponseCode = NOT_FOUND,
        reportAs = NOT_FOUND,
        headers = Map.empty
      )
      circuitBreaker.breakOnExceptionDelegate(exception) should be (false)
    }

    "count exception for 5xx" in new TestCase {
      val exception = new Upstream5xxResponse(
        message = "",
        upstreamResponseCode = INTERNAL_SERVER_ERROR,
        reportAs = INTERNAL_SERVER_ERROR
      )
      circuitBreaker.breakOnExceptionDelegate(exception) should be (true)
    }

    "count any non Upstream exception" in new TestCase {
      circuitBreaker.breakOnExceptionDelegate(new ConnectException()) should be (true)
    }
  }

  trait TestCase {
    val circuitBreaker = new ServicesCircuitBreaker with ServicesConfig {

      override val externalServiceName: String = "test service"

      def breakOnExceptionDelegate(t: Throwable) = breakOnException(t)
    }
  }
}
