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

class SearchClientSpec extends BaseSpec with MockitoSugar with BeforeAndAfter {

  var keyStore: KeyStoreMicroService = _
  var controller: SearchClientController = _

  val id = "wshakespeare"
  val authority = s"/auth/oid/$id"
  val uri = "/personal/paye/blah"
  val payeRoot = PayeRoot("CE927349E", 1, "Mr", "Will", None, "Shakespeare", "Will Shakespeare", "1983-01-02", Map(), Map(), Map())
  val user = User(id, null, RegimeRoots(Some(payeRoot), None, None, None, None), None, None)

  before {
    keyStore = mock[KeyStoreMicroService]
    controller = new SearchClientController(keyStore)
  }

  "Given that Bob is on the search screen the page" should {
    "show errors on the form when we make a submission with no values" in new WithApplication(FakeApplication()) {
      val result = executeSearchActionWith(nino="", firstName="", lastName="", dob=("", "", ""))

      status(result) shouldBe 400

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s".error #${controller.nino}") should not be 'empty
    }

    "show errors on the form when we make a submission with invalid values" in new WithApplication(FakeApplication()) {
      val result = executeSearchActionWith(nino="XXX", firstName="123", lastName="alert('foo')", dob=("1","1", LocalDate.now().minusYears(111).getYear.toString))

      status(result) shouldBe 400

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s".error #${controller.nino}") should not be 'empty
      doc.select(s".error #${controller.firstName}") should not be ('empty)
      doc.select(s".error #${controller.lastName}") should not be ('empty)
      doc.select(".error select") should not be 'empty     //TODO: Due to id with . we have this selection instead of #dob.date, not perfect
    }

    "show errors on the form when we make a submission with only spaces for names" in new WithApplication(FakeApplication()) {
      val result = executeSearchActionWith(nino="XXX", firstName="      ", lastName="      ", dob=("1","1", LocalDate.now().minusYears(111).getYear.toString))

      status(result) shouldBe 400

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s".error #${controller.nino}") should not be 'empty
      doc.select(".error select") should not be 'empty     //TODO: Due to id with . we have this selection instead of #dob.date, not perfect
    }

    "show global error on the form when we fill in nino and only one other field" in new WithApplication(FakeApplication()) {
      val result = executeSearchActionWith(nino="AB123456C", firstName="hasNoValidation", lastName="", dob=("", "", ""))

      status(result) shouldBe 400

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s".error #${controller.nino}") should be ('empty)
      doc.select(".error #globalErrors") should not be 'empty     //TODO: Due to id with . we have this selection instead of #dob.date, not perfect
    }

    "not show any errors on the form when we make a submission with valid nino, firstName, lastName, dob" in new WithApplication(FakeApplication()) {
      val result = executeSearchActionWith(nino="AB123456C", firstName="firstName", lastName="lastName", dob=("1","1", "1990"))

      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s"#clientSearchResults #${controller.nino}").text should include ("AB123456C")
      doc.select(s"#clientSearchResults #${controller.firstName}").text should include ("firstName")
      doc.select(s"#clientSearchResults #${controller.lastName}").text should include ("lastName")
      doc.select(s"#clientSearchResults #${controller.dob}").text should include ("January 1, 1990")
    }

    "not show any errors on the form when we make a submission with valid nino, firstName, lastName" in new WithApplication(FakeApplication()) {
      val result = executeSearchActionWith(nino="AB123456C", firstName="firstName", lastName="lastName", dob=("","", ""))

      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s"#clientSearchResults #${controller.nino}").text should include ("AB123456C")
      doc.select(s"#clientSearchResults #${controller.firstName}").text should include ("firstName")
      doc.select(s"#clientSearchResults #${controller.lastName}").text should include ("lastName")
      doc.select(s"#clientSearchResults #${controller.dob}") should be ('empty)
    }

    "not show any errors on the form when we make a submission with valid nino, firstName, dob" in new WithApplication(FakeApplication()) {
      val result = executeSearchActionWith(nino="AB123456C", firstName="firstName", lastName="", dob=("1","1", "1990"))

      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s"#clientSearchResults #${controller.nino}").text should include ("AB123456C")
      doc.select(s"#clientSearchResults #${controller.firstName}").text should include ("firstName")
      doc.select(s"#clientSearchResults #${controller.lastName}") should be (empty)
      doc.select(s"#clientSearchResults #${controller.dob}").text should include ("January 1, 1990")
    }

    "not show any errors on the form when we make a submission with valid nino, lastName, dob" in new WithApplication(FakeApplication()) {
      val result = executeSearchActionWith(nino="AB123456C", firstName="", lastName="lastName", dob=("1","1", "1990"))

      status(result) shouldBe 200

      val doc = Jsoup.parse(contentAsString(result))
      doc.select(s"#clientSearchResults #${controller.nino}").text should include ("AB123456C")
      doc.select(s"#clientSearchResults #${controller.firstName}") should be (empty)
      doc.select(s"#clientSearchResults #${controller.lastName}").text should include ("lastName")
      doc.select(s"#clientSearchResults #${controller.dob}").text should include ("January 1, 1990")
    }

    "not save anything to keystore when we make a submission with errors" in new WithApplication(FakeApplication()) {
      val result = executeSearchActionWith(nino="", firstName="", lastName="", dob=("", "", ""))
      status(result) shouldBe 400
      verifyZeroInteractions(keyStore)
    }

    "save the client search results to the keystore when we make a successful submission" in new WithApplication(FakeApplication()) {
      val clientSearch = ClientSearch("AB123456C", Some("firstName"), Some("lastName"), Some(new LocalDate(1990, 1, 1)))
      val result = executeSearchActionWith(clientSearch.nino, clientSearch.firstName.get, clientSearch.lastName.get,
        (clientSearch.dob.get.getDayOfMonth.toString,clientSearch.dob.get.getMonthOfYear.toString, clientSearch.dob.get.getYear.toString))
      status(result) shouldBe 200
      verify(keyStore).addKeyStoreEntry(controller.keystoreId(user.oid), controller.serviceSourceKey, controller.clientSearchObjectKey, clientSearch)
    }

    def executeSearchActionWith(nino: String, firstName: String, lastName: String, dob: (String, String, String)) = {
      controller.searchAction(user, FakeRequest().withFormUrlEncodedBody(("nino", nino),
        ("firstName", firstName),
        ("lastName", lastName),
        ("dob.day", dob._1),
        ("dob.month", dob._2),
        ("dob.year", dob._3)))
    }
  }


//  Acceptance Criteria (Happy Path)
//
//  Agent Bob searches for a client
//
//  Given that Bob is on the search screen
//    And the values entered into the fields are valid
//  When he executes the search
//  And there is a matching client returned from CID
//  And the same matching client returned from NPS
//    Then he should be taken to the results screen
//
//  Agent Bob's search matches more than one client in CID
//
//  Given that Bob is on the search screen
//    And the values entered into the fields are valid
//  When he executes the search
//  And there is more than 1 matching client returned from CID
//  And the second search in NPS with the first record from the return from CID matches the same client returned from NPS
//  Then he should be taken to the results screen
//
//  Acceptance Criteria (Unhappy path)
//
//  Agent Bob's search matches more than one client in NPS

//  Given that Bob is on the search screen
//    And the values entered into the fields are valid
//  When he executes the search
//  And there is one or more matching clients returned from CID
//  And the second search in NPS with the first record from the return from CID matches more than one client in NPS
//  Then he should *not* be taken to the results screen but should remain on the search screen
//  And he should be prompted that the search returned no matches
//
//  Agent Bob searches for a client and finds a match in CID but not NPS
//
//  Given that Bob is on the search screen
//    And the values entered into the fields are valid
//  When he executes the search
//  And there is a matching client returned from CID
//  And there is no matching client returned from NPS
//  Then he should *not* be taken to the results screen but should remain on the search screen
//  And he should be prompted that the search returned no matches
//
//  Agent Bob searches for a client and finds no match in CID
//
//  Given that Bob is on the search screen
//    And the values entered into the fields are valid
//  When he executes the search
//  And there is no matching client returned from CID
//  Then he should *not* be taken to the results screen but should remain on the search screen
//  And he should be prompted that the search returned no matches
//
}
