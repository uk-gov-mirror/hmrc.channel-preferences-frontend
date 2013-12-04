package controllers.bt.accountsummary

import org.joda.time.LocalDate

import uk.gov.hmrc.common.BaseSpec
import controllers.bt.routes
import controllers.bt.accountsummary.SaMessageKeys._
import controllers.bt.accountsummary.CommonBusinessMessageKeys._
import views.helpers.MoneyPounds
import uk.gov.hmrc.common.microservice.sa.domain.Liability
import uk.gov.hmrc.common.microservice.sa.domain.SaAccountSummary
import uk.gov.hmrc.common.microservice.sa.domain.AmountDue
import uk.gov.hmrc.domain.SaUtr

class SaAccountSummaryBuilderSpec extends BaseSpec {
  private val homeUrl = "http://home"
  private val makeAPaymentUrl = routes.PaymentController.makeSaPayment().url
  private val liabilityDate = new LocalDate(2014, 1, 15)
  private val saUtr = SaUtr("123456789")
  private val utrMessage = Msg("sa.message.utr", Seq(saUtr.utr))

  val saAsBuilder = new SaASBuild {
    def saPaymentUrl: String = makeAPaymentUrl

    def buildPortalUrl(s: String) = s match {
      case saHomePortalUrl => homeUrl
    }
  }

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
      val totalAmountDueToHmrc = Some(AmountDue(amountDue, requiresPayment = true))
      val nextPayment = None
      val amountHmrcOwe = Some(BigDecimal(0))

      val accountSummary = SaAccountSummary(totalAmountDueToHmrc, nextPayment, amountHmrcOwe)
      val expectedMessages = Seq(Msg(saAmountDueForPaymentMessage, Seq(MoneyPounds(amountDue))), Msg(saInterestApplicableMessage, Seq.empty))

      testSaAccountSummaryBuilder(accountSummary, expectedMessages)

    }

    "when amounts are due now and later " in {
      val amountDue = BigDecimal(100)
      val totalAmountDueToHmrc = Some(AmountDue(amountDue, requiresPayment = true))

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
      val totalAmountDueToHmrc = Some(AmountDue(BigDecimal(0), requiresPayment = false))
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
      val totalAmountDueToHmrc = Some(AmountDue(amountDue, requiresPayment = false))
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
      val expectedMessages =
        Seq(
          Msg(saSummaryUnavailableErrorMessage1),
          Msg(saSummaryUnavailableErrorMessage2),
          Msg(saSummaryUnavailableErrorMessage3),
          Msg(saSummaryUnavailableErrorMessage4)
        )

      testSaAccountSummaryBuilder(None, expectedMessages, List())
    }
  }

  val goodLinks = Seq(
    AccountSummaryLink("sa-account-details-href", homeUrl, viewAccountDetailsLinkMessage, sso = true),
    AccountSummaryLink("sa-make-payment-href", makeAPaymentUrl, makeAPaymentLinkMessage, sso = false),
    AccountSummaryLink("sa-file-return-href", homeUrl, fileAReturnLinkMessage, sso = true)
  )

  def testSaAccountSummaryBuilder(accountSummary: SaAccountSummary, expectedMessages: Seq[Msg], expectedLinks: Seq[AccountSummaryLink] = goodLinks): Unit =
    testSaAccountSummaryBuilder(Some(accountSummary), expectedMessages, expectedLinks)

  def testSaAccountSummaryBuilder(aso: Option[SaAccountSummary], expectedMessages: Seq[Msg], expectedLinks: Seq[AccountSummaryLink]): Unit = {
    val actualAccountSummary = saAsBuilder.build(aso, saUtr)

    actualAccountSummary.regimeName shouldBe SaMessageKeys.saRegimeName
    actualAccountSummary.messages shouldBe utrMessage +: expectedMessages

    actualAccountSummary.addenda shouldBe expectedLinks
  }

}