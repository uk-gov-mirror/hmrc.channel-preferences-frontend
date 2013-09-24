package uk.gov.hmrc.common.microservice.sa.domain

import uk.gov.hmrc.common.BaseSpec
import org.mockito.Mockito._
import uk.gov.hmrc.common.microservice.sa.SaMicroService
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.common.microservice.sa.domain.write.SaAddressForUpdate
import org.mockito.Matchers
import uk.gov.hmrc.domain.SaUtr

class SaRootSpec extends BaseSpec with MockitoSugar {

  "VatRoot personalDetails" should {

    "call the SA microservice when the uri is found in the SaRoot and return its value" in {
      val uri = "sa/individual/12345/personalDetails"
      val saRoot = SaRoot("12345", Map("individual/details" -> uri))
      val name = SaName("Mr", "Tim", None, "Smith", None)
      val saPersonalDetails = Some(SaPerson("tim", name, SaIndividualAddress("line1", "line2", Some("line3"), Some("line4"), Some("line5"), Some("46353"), Some("Malta"),None)))
      val saMicroService = mock[SaMicroService]

      when(saMicroService.person(uri)).thenReturn(saPersonalDetails)

      saRoot.personalDetails(saMicroService) shouldBe saPersonalDetails
      verify(saMicroService).person(Matchers.eq(uri))
    }

    "return None when the personal details link is not present" in {
      val saRoot = SaRoot("12345", Map.empty)
      val saMicroService = mock[SaMicroService]

      saRoot.personalDetails(saMicroService) shouldBe None
      verify(saMicroService, times(0)).person(Matchers.anyString())
    }

  }

  "VatRoot accountSummary" should {

    "call the SA microservice when the uri is found in the SaRoot and return its value" in {
      val uri = "sa/individual/12345/accountSummary"
      val saRoot = SaRoot("12345", Map("individual/account-summary" -> uri))
      val accountSummary = Some(SaAccountSummary(Some(AmountDue(BigDecimal(30.2), false)), None, Some(BigDecimal(454.2))))
      val saMicroService = mock[SaMicroService]

      when(saMicroService.accountSummary(uri)).thenReturn(accountSummary)

      saRoot.accountSummary(saMicroService) shouldBe accountSummary
      verify(saMicroService).accountSummary(Matchers.eq(uri))
    }

    "return None when the account summary link is not present" in {
      val saRoot = SaRoot("12345", Map.empty)
      val saMicroService = mock[SaMicroService]

      saRoot.accountSummary(saMicroService) shouldBe None
      verify(saMicroService, times(0)).accountSummary(Matchers.anyString())
    }

  }

  "VatRoot updateIndividualMainAddress" should {

    "call the SA microservice when the uri is found in the SaRoot for updating the main address" in {
      val uri = "sa/individual/12345/mainAddress"
      val saRoot = SaRoot("12345", Map("individual/details/main-address" -> uri))
      val saMainAddress = SaAddressForUpdate("line1", "line2", None, None, None, None)
      implicit val saMicroService = mock[SaMicroService]
      val transactionId = Right(TransactionId("12343asdfkjhaslkdfhoi3243kjh3kj4h343"))

      when(saMicroService.updateMainAddress(uri, saMainAddress)).thenReturn(transactionId)

      saRoot.updateIndividualMainAddress(saMainAddress) shouldBe transactionId
      verify(saMicroService).updateMainAddress(Matchers.eq(uri), Matchers.any())
    }

    "throw a IllegalStateException when link the uri is not found in the SaRoot" in {
      val saRoot = SaRoot("12345", Map.empty)
      implicit val saMicroService = mock[SaMicroService]
      val saMainAddress = SaAddressForUpdate("line1", "line2", None, None, None, None)

      evaluating(saRoot.updateIndividualMainAddress(saMainAddress)) should produce[IllegalStateException]
      verify(saMicroService, times(0)).accountSummary(Matchers.anyString())
    }

  }

}
