package controllers.agent.addClient

import uk.gov.hmrc.common.BaseSpec
import org.scalatest.mock.MockitoSugar
import play.api.test.{FakeRequest, WithApplication}
import play.api.test.Helpers._
import play.api.test.FakeApplication
import org.jsoup.Jsoup
import uk.gov.hmrc.common.microservice.domain.{RegimeRoots, User}
import uk.gov.hmrc.common.microservice.paye.domain.PayeRoot
import org.joda.time.LocalDate
import uk.gov.hmrc.common.microservice.keystore.KeyStoreMicroService
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.scalatest.BeforeAndAfter
import models.agent.addClient.ClientSearch
import scala.util.Success
import uk.gov.hmrc.common.microservice.agent.{MatchingPerson, SearchRequest, AgentMicroService}
import SearchClientController.KeyStoreKeys
import SearchClientController.FieldIds

class SearchClientSpec extends BaseSpec with MockitoSugar with BeforeAndAfter {

  var keyStore: KeyStoreMicroService = _
  var agentService: AgentMicroService = _
  var controller: SearchClientController = _

  val id = "wshakespeare"
  val authority = s"/auth/oid/$id"
  val uri = "/personal/paye/blah"
  val payeRoot = PayeRoot("CE927349E", 1, "Mr", "Will", None, "Shakespeare", "Will Shakespeare", "1983-01-02", Map(), Map(), Map())
  val user = User(id, null, RegimeRoots(Some(Success(payeRoot)), None, None, None, None), None, None)

  before {
    keyStore = mock[KeyStoreMicroService]
    agentService = mock[AgentMicroService]
    controller = new SearchClientController(keyStore) {
      override implicit lazy val agentMicroService: AgentMicroService = agentService
    }
  }

  "Given that Bob is on the search screen the page" should {
    "show errors on the form when we make a submission with no values" in new WithApplication(FakeApplication()) {
      val result = executeSearchActionWith(nino="", firstName="", lastName="", dob=("", "", ""))

      status(result) shouldBe 400

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s".error #${FieldIds.nino}") should not be 'empty
    }

    "show errors on the form when we make a submission with invalid values" in new WithApplication(FakeApplication()) {
      val result = executeSearchActionWith(nino="XXX", firstName="&&%", lastName="alert('foo')", dob=("1","1", LocalDate.now().minusYears(111).getYear.toString))

      status(result) shouldBe 400

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s".error #${FieldIds.nino}") should not be 'empty
      doc.select(s".error #${FieldIds.firstName}") should not be ('empty)
      doc.select(s".error #${FieldIds.lastName}") should not be ('empty)
      doc.select(".error select") should not be 'empty     //TODO: Due to id with . we have this selection instead of #dob.date, not perfect
    }

    "show errors on the form when we make a submission with only spaces for names" in new WithApplication(FakeApplication()) {
      val result = executeSearchActionWith(nino="XXX", firstName="      ", lastName="      ", dob=("1","1", LocalDate.now().minusYears(111).getYear.toString))

      status(result) shouldBe 400

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s".error #${FieldIds.nino}") should not be 'empty
      doc.select(".error select") should not be 'empty     //TODO: Due to id with . we have this selection instead of #dob.date, not perfect
    }

    "show global error on the form when we fill in nino and only one other field" in new WithApplication(FakeApplication()) {
      val result = executeSearchActionWith(nino="AB123456C", firstName="hasNoValidation", lastName="", dob=("", "", ""))

      status(result) shouldBe 400

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s".error #${FieldIds.nino}") should be ('empty)
      doc.select(".error #globalErrors") should not be 'empty
    }

    "allow a submission with valid nino, firstName, lastName, dob and display all fields" in new WithApplication(FakeApplication()) {
      givenTheAgentServiceReturnsAMatch()

      val result = executeSearchActionWith(nino="AB123456C", firstName="firstName", lastName="lastName", dob=("1","1", "1990"))

      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s"#clientSearchResults #${FieldIds.nino}").text should include ("AB123456C")
      doc.select(s"#clientSearchResults #${FieldIds.firstName}").text should include ("resFirstName")
      doc.select(s"#clientSearchResults #${FieldIds.lastName}").text should include ("resLastName")
      doc.select(s"#clientSearchResults #${FieldIds.dob}").text should include ("January 1, 1991")
    }

    "allow a submission with valid nino, firstName, lastName and not display the dob" in new WithApplication(FakeApplication()) {
      givenTheAgentServiceReturnsAMatch()

      val result = executeSearchActionWith(nino="AB123456C", firstName="firstName", lastName="lastName", dob=("","", ""))

      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s"#clientSearchResults #${FieldIds.nino}").text should include ("AB123456C")
      doc.select(s"#clientSearchResults #${FieldIds.firstName}").text should include ("resFirstName")
      doc.select(s"#clientSearchResults #${FieldIds.lastName}").text should include ("resLastName")
      doc.select(s"#clientSearchResults #${FieldIds.dob}") should be ('empty)
    }

    "allow a submission with valid nino, firstName, dob and not display the lastname" in new WithApplication(FakeApplication()) {
      givenTheAgentServiceReturnsAMatch()

      val result = executeSearchActionWith(nino="AB123456C", firstName="firstName", lastName="", dob=("1","1", "1990"))

      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s"#clientSearchResults #${FieldIds.nino}").text should include ("AB123456C")
      doc.select(s"#clientSearchResults #${FieldIds.firstName}").text should include ("resFirstName")
      doc.select(s"#clientSearchResults #${FieldIds.lastName}") should be (empty)
      doc.select(s"#clientSearchResults #${FieldIds.dob}").text should include ("January 1, 1991")
    }

    "allow a submission with valid nino, lastName, dob and not display the firstname" in new WithApplication(FakeApplication()) {
      givenTheAgentServiceReturnsAMatch()

      val result = executeSearchActionWith(nino="AB123456C", firstName="", lastName="lastName", dob=("1","1", "1990"))

      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s"#clientSearchResults #${FieldIds.nino}").text should include ("AB123456C")
      doc.select(s"#clientSearchResults #${FieldIds.firstName}") should be (empty)
      doc.select(s"#clientSearchResults #${FieldIds.lastName}").text should include ("resLastName")
      doc.select(s"#clientSearchResults #${FieldIds.dob}").text should include ("January 1, 1991")
    }

    "not save anything to keystore when we make a submission with errors" in new WithApplication(FakeApplication()) {
      val result = executeSearchActionWith(nino="", firstName="", lastName="", dob=("", "", ""))
      status(result) shouldBe 400
      verifyZeroInteractions(keyStore)
    }

    "save the client search results to the keystore when we make a successful submission with all fields" in new WithApplication(FakeApplication()) {
      val clientSearch = ClientSearch("AB123456C", Some("firstName"), Some("lastName"), Some(new LocalDate(1990, 1, 1)))
      givenTheAgentServiceReturnsAMatch()
      val result = executeSearchActionWith(clientSearch.nino, clientSearch.firstName.get, clientSearch.lastName.get,
        (clientSearch.dob.get.getDayOfMonth.toString,clientSearch.dob.get.getMonthOfYear.toString, clientSearch.dob.get.getYear.toString))
      status(result) shouldBe 200
      verify(keyStore).addKeyStoreEntry(KeyStoreKeys.keystoreId(user.oid), KeyStoreKeys.serviceSourceKey, KeyStoreKeys.clientSearchObjectKey,
        MatchingPerson("AB123456C",Some("resFirstName"),Some("resLastName"),Some("1991-01-01")))
    }

    "save partial client search results to the keystore when we make a successful submission with some fields" in new WithApplication(FakeApplication()) {
      val clientSearch = ClientSearch("AB123456C", Some("firstName"), Some("lastName"), Some(new LocalDate(1990, 1, 1)))
      givenTheAgentServiceReturnsAMatch()
      val result = executeSearchActionWith(clientSearch.nino, clientSearch.firstName.get, clientSearch.lastName.get,
        ("", "", ""))
      status(result) shouldBe 200
      verify(keyStore).addKeyStoreEntry(KeyStoreKeys.keystoreId(user.oid), KeyStoreKeys.serviceSourceKey, KeyStoreKeys.clientSearchObjectKey,
        MatchingPerson("AB123456C",Some("resFirstName"),Some("resLastName"),None))
    }

    "display an error when no match is found and not save anything to the keystore" in new WithApplication(FakeApplication()) {
      givenTheAgentServiceFindsNoMatch()
      val result = executeSearchActionWith(nino="AB123456C", firstName="", lastName="lastName", dob=("1","1", "1990"))

      status(result) shouldBe 404
      verifyZeroInteractions(keyStore)

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(".error #globalErrors") should not be 'empty
    }

    def executeSearchActionWith(nino: String, firstName: String, lastName: String, dob: (String, String, String)) = {
      val request = FakeRequest().withFormUrlEncodedBody(("nino", nino),
        ("firstName", firstName),
        ("lastName", lastName),
        ("dob.day", dob._1),
        ("dob.month", dob._2),
        ("dob.year", dob._3))
      controller.searchAction(user)(request)
    }

    def givenTheAgentServiceFindsNoMatch() = when(agentService.searchClient(any[SearchRequest])).thenReturn(None)
    def givenTheAgentServiceReturnsAMatch() =
      when(agentService.searchClient(any[SearchRequest])).thenReturn(Some(MatchingPerson("AB123456C", Some("resFirstName"), Some("resLastName"), Some("1991-01-01"))))
  }

}
