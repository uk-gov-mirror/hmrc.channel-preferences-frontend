package uk.gov.hmrc.common.microservice.audit

import org.scalatest.{ Matchers, WordSpec }
import play.api.libs.json.JsValue
import play.api.test.{ FakeApplication, WithApplication }

class TestAuditMicroService extends AuditMicroService {
  var body: JsValue = null
  var headers: Map[String, String] = null

  override protected def httpPostAndForget(uri: String, body: JsValue, headers: Map[String, String] = Map.empty) {
    this.body = body
    this.headers = headers
  }
}

class AuditMicroServiceSpec extends WordSpec with Matchers {

  "AuditMicroService enabled" should {
    "call the audit service with an audit event" in new WithApplication(FakeApplication(additionalConfiguration = Map("govuk-tax.Test.services.audit.enabled" -> true))) {
      val auditMicroService = new TestAuditMicroService()

      val auditEvent = AuditEvent("frontend", "request", Map("userId" -> "/auth/oid/099990"), Map("name" -> "Fred"))
      auditMicroService.audit(auditEvent)

      auditMicroService.headers should be(Map.empty)

      val body = auditMicroService.body
      (body \ "auditSource").as[String] should be("frontend")
      (body \ "auditType").as[String] should be("request")
      (body \ "tags" \ "userId").as[String] should be("/auth/oid/099990")
      (body \ "detail" \ "name").as[String] should be("Fred")
    }
  }

  "AuditMicroService disabled" should {
    "call the audit service with an audit event" in new WithApplication(FakeApplication(additionalConfiguration = Map("govuk-tax.Test.services.audit.enabled" -> false))) {

      val auditMicroService = new TestAuditMicroService()
      val auditEvent = AuditEvent("frontend", "request", Map("userId" -> "/auth/oid/099990"), Map("name" -> "Fred"))
      auditMicroService.audit(auditEvent)

      auditMicroService.body should be(null)
      auditMicroService.headers should be(null)

    }
  }
}

