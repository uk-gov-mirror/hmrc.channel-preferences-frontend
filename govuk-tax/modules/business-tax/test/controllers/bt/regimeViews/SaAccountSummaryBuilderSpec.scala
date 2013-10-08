package controllers.bt.regimeViews

import uk.gov.hmrc.common.BaseSpec
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.common.microservice.sa.SaConnector
import org.mockito.Mockito._
import views.helpers._
import org.joda.time.LocalDate
import controllers.bt.routes
import controllers.bt.regimeViews.SaMessageKeys._
import views.helpers.LinkMessage
import uk.gov.hmrc.common.microservice.sa.domain.Liability
import uk.gov.hmrc.common.microservice.sa.domain.SaRoot
import views.helpers.RenderableLinkMessage
import uk.gov.hmrc.common.microservice.sa.domain.SaAccountSummary
import uk.gov.hmrc.common.microservice.domain.User
import uk.gov.hmrc.common.microservice.domain.RegimeRoots
import uk.gov.hmrc.common.microservice.sa.domain.AmountDue
import SaPortalUrlKeys._
import scala.util.Success

class SaAccountSummaryBuilderSpec extends BaseSpec with MockitoSugar {
  private val homeUrl = "http://home"
  private val makeAPaymentUrl = routes.BusinessTaxController.makeAPaymentLanding().url
  private val liabilityDate = new LocalDate(2014, 1, 15)


  "Sa Account SummaryView Builder builds correct Account Summary model " should {
    " when no amounts are due now or later " in {
      val totalAmountDueToHmrc = None
      val nextPayment = None
      val amountHmrcOwe = None

      val accountSummary = SaAccountSummary(totalAmountDueToHmrc, nextPayment, amountHmrcOwe)
      val expectedMessages = Seq(Msg(saNothingToPayMessage, Seq.empty))

      testSaAccountSummaryBuilder(accountSummary, expectedMessages)
    }

    "when an amount is due now (payable) " in {
      val amountDue = BigDecimal(100)
      val totalAmountDueToHmrc = Some(AmountDue(amountDue, true))
      val nextPayment = None
      val amountHmrcOwe = Some(BigDecimal(0))

      val accountSummary = SaAccountSummary(totalAmountDueToHmrc, nextPayment, amountHmrcOwe)
      val expectedMessages = Seq(Msg(saAmountDueForPaymentMessage, Seq(MoneyPounds(amountDue))), Msg(saInterestApplicableMessage, Seq.empty))

      testSaAccountSummaryBuilder(accountSummary, expectedMessages)

    }

    "when amounts are due now and later " in {
      val amountDue = BigDecimal(100)
      val totalAmountDueToHmrc = Some(AmountDue(amountDue, true))

      val liabilityAmount = BigDecimal(20)
      val nextPayment = Some(Liability(new LocalDate(2014, 1, 15), liabilityAmount))
      val amountHmrcOwe = Some(BigDecimal(0))

      val accountSummary = SaAccountSummary(totalAmountDueToHmrc, nextPayment, amountHmrcOwe)
      val expectedMessages = Seq(Msg(saAmountDueForPaymentMessage, Seq(MoneyPounds(amountDue))), Msg(saInterestApplicableMessage),
        Msg(saWillBecomeDueMessage, Seq(MoneyPounds(liabilityAmount), liabilityDate)))

      testSaAccountSummaryBuilder(accountSummary, expectedMessages)

    }

    "when an amount is due for repayment to the user " in {
      val totalAmountDueToHmrc = None
      val nextPayment = None
      val amountHmrcOwe = Some(BigDecimal(100))

      val accountSummary = SaAccountSummary(totalAmountDueToHmrc, nextPayment, amountHmrcOwe)
      val expectedMessages = Seq[Msg](
        Msg(saYouHaveOverpaidMessage),
        Msg(saAmountDueForRepaymentMessage, Seq(MoneyPounds(amountHmrcOwe.get))))

      testSaAccountSummaryBuilder(accountSummary, expectedMessages)

    }

    "when an amount is due for repayment to the user and an amount is becoming due for repayment " in {
      val totalAmountDueToHmrc = None
      val liabilityAmount = BigDecimal(20)
      val nextPayment = Some(Liability(new LocalDate(2014, 1, 15), liabilityAmount))
      val amountHmrcOwe = Some(BigDecimal(100))

      val accountSummary = SaAccountSummary(totalAmountDueToHmrc, nextPayment, amountHmrcOwe)
      val expectedMessages = Seq[Msg](
        Msg(saYouHaveOverpaidMessage), Msg(saAmountDueForRepaymentMessage, Seq(MoneyPounds(amountHmrcOwe.get))),
        Msg(saWillBecomeDueMessage, Seq(MoneyPounds(liabilityAmount), liabilityDate)))
      testSaAccountSummaryBuilder(accountSummary, expectedMessages)
    }

    "when no amounts due currently but amounts becoming due for payment " in {
      val totalAmountDueToHmrc = Some(AmountDue(BigDecimal(0), false))
      val liabilityAmount = BigDecimal(20)
      val nextPayment = Some(Liability(new LocalDate(2014, 1, 15), liabilityAmount))
      val amountHmrcOwe = Some(BigDecimal(0))

      val accountSummary = SaAccountSummary(totalAmountDueToHmrc, nextPayment, amountHmrcOwe)
      val expectedMessages = Seq[Msg](
        Msg(saNothingToPayMessage, Seq.empty),
        Msg(saWillBecomeDueMessage, Seq(MoneyPounds(liabilityAmount), liabilityDate))
      )
      testSaAccountSummaryBuilder(accountSummary, expectedMessages)
    }

    "when amount due for payment (not payable) and other becoming due " in {
      val amountDue = BigDecimal(10)
      val totalAmountDueToHmrc = Some(AmountDue(amountDue, false))
      val liabilityAmount = BigDecimal(20)
      val nextPayment = Some(Liability(new LocalDate(2014, 1, 15), liabilityAmount))
      val amountHmrcOwe = None

      val accountSummary = SaAccountSummary(totalAmountDueToHmrc, nextPayment, amountHmrcOwe)
      val expectedMessages = Seq(
        Msg(saAmountDueForPaymentMessage, Seq(MoneyPounds(amountDue))),
        Msg(saSmallAmountToPayMessage),
        Msg(saWillBecomeDueMessage, Seq(MoneyPounds(liabilityAmount), liabilityDate))
      )
      testSaAccountSummaryBuilder(accountSummary, expectedMessages)
    }

    "when account summary is not available " in {
      val mockUser = mock[User]
      val mockSaConnector = mock[SaConnector]
      val mockRegimeRoots = mock[RegimeRoots]
      val mockSaRoot = mock[SaRoot]

      val mockPortalUrlBuilder = mock[DummyPortalUrlBuilder]

      when(mockUser.regimes).thenReturn(mockRegimeRoots)
      when(mockRegimeRoots.sa).thenReturn(Some(Success(mockSaRoot)))
      when(mockSaRoot.accountSummary(mockSaConnector)).thenThrow(new RuntimeException)

      val expectedMessages =
        Seq(
          Msg(saSummaryUnavailableErrorMessage1),
          Msg(saSummaryUnavailableErrorMessage2),
          Msg(saSummaryUnavailableErrorMessage3),
          Msg(saSummaryUnavailableErrorMessage4)
        )

      val actualAccountSummary = SaAccountSummaryBuilder(mockSaConnector).build(mockPortalUrlBuilder.build, mockUser).get
      actualAccountSummary.regimeName shouldBe saRegimeName
      actualAccountSummary.messages shouldBe expectedMessages

    }

  }

  trait DummyPortalUrlBuilder {
    def build(a: String): String
  }


  private def testSaAccountSummaryBuilder(accountSummary: SaAccountSummary, expectedMessages: Seq[Msg]) {
    val mockUser = mock[User]
    val mockSaConnector = mock[SaConnector]
    val mockRegimeRoots = mock[RegimeRoots]
    val mockSaRoot = mock[SaRoot]

    val mockPortalUrlBuilder = mock[DummyPortalUrlBuilder]

    when(mockUser.regimes).thenReturn(mockRegimeRoots)
    when(mockRegimeRoots.sa).thenReturn(Some(Success(mockSaRoot)))
    when(mockSaRoot.accountSummary(mockSaConnector)).thenReturn(Some(accountSummary))

    when(mockPortalUrlBuilder.build(saHomePortalUrl)).thenReturn(homeUrl)
    when(mockPortalUrlBuilder.build(makeAPaymentLinkMessage)).thenReturn(makeAPaymentUrl)

    val actualAccountSummary = SaAccountSummaryBuilder(mockSaConnector).build(mockPortalUrlBuilder.build, mockUser).get

    actualAccountSummary.regimeName shouldBe SaMessageKeys.saRegimeName

    actualAccountSummary.messages shouldBe expectedMessages

    val expectedLinks = Seq(
      RenderableLinkMessage(LinkMessage(homeUrl, viewAccountDetailsLinkMessage, Some("portalLink"))),
      RenderableLinkMessage(LinkMessage(makeAPaymentUrl, makeAPaymentLinkMessage)),
      RenderableLinkMessage(LinkMessage(homeUrl, fileAReturnLinkMessage))

    )
    actualAccountSummary.addenda shouldBe expectedLinks
  }

}