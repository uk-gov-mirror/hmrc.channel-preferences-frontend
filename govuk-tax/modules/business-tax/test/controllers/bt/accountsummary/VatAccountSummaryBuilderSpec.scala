package controllers.bt.accountsummary

import uk.gov.hmrc.common.BaseSpec
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.common.microservice.domain.{RegimeRoots, User}
import uk.gov.hmrc.common.microservice.vat.VatConnector
import uk.gov.hmrc.common.microservice.auth.domain.{Regimes, UserAuthority}
import org.mockito.Mockito._
import views.helpers.{MoneyPounds, RenderableMessage, LinkMessage}
import uk.gov.hmrc.domain.Vrn
import VatMessageKeys._
import VatPortalUrls._
import CommonBusinessMessageKeys._
import uk.gov.hmrc.common.microservice.vat.domain.{VatAccountBalance, VatAccountSummary, VatRoot}
import controllers.common.actions.HeaderCarrier
import scala.concurrent.Future
import controllers.domain.AuthorityUtils._
import uk.gov.hmrc.common.microservice.auth.domain.UserAuthority
import uk.gov.hmrc.common.microservice.vat.domain.VatAccountSummary
import scala.Some
import uk.gov.hmrc.domain.Vrn
import controllers.bt.accountsummary.VatAccountSummaryBuilder
import uk.gov.hmrc.common.microservice.vat.domain.VatAccountBalance
import views.helpers.MoneyPounds
import uk.gov.hmrc.common.microservice.auth.domain.Regimes
import uk.gov.hmrc.common.microservice.domain.User
import uk.gov.hmrc.common.microservice.domain.RegimeRoots
import controllers.bt.accountsummary.AccountSummary
import controllers.bt.accountsummary.Msg

class VatAccountSummaryBuilderSpec extends BaseSpec with MockitoSugar {

  val buildPortalUrl: (String) => String = (value: String) => value
  val vrn = Vrn("12345")
  val userAuthorityWithVrn = UserAuthority("123", Regimes(), vrn = Some(vrn))
  val aDate = "2012-06-06"
  val regimeRootsWithVat = RegimeRoots(vat = Some(VatRoot(vrn, Map("accountSummary" -> s"/vat/${vrn.vrn}"))))
  val userEnrolledForVat = User("jim", vatAuthority("jim", "12345"), regimeRootsWithVat, None, None)

  "VatAccountSummaryViewBuilder" should {
    "return the correct account summary for complete data" in {

      val accountSummary = VatAccountSummary(Some(VatAccountBalance(Some(6.1))), Some(aDate))
      val mockVatConnector = mock[VatConnector]
      when(mockVatConnector.accountSummary(s"/vat/${vrn.vrn}")).thenReturn(Future.successful(Some(accountSummary)))
      val builder: VatAccountSummaryBuilder = VatAccountSummaryBuilder(mockVatConnector)
      val accountSummaryViewOption = builder.build(buildPortalUrl, userEnrolledForVat)
      accountSummaryViewOption shouldNot be(None)
      val accountSummaryView = accountSummaryViewOption.get
      accountSummaryView.regimeName shouldBe vatRegimeNameMessage
      accountSummaryView.messages shouldBe Seq[Msg](Msg(vatRegistrationNumberMessage ,Seq("12345")), Msg(vatAccountBalanceMessage,Seq(MoneyPounds(BigDecimal(6.1)))) )
      accountSummaryView.addenda shouldBe Seq[RenderableMessage](LinkMessage(vatAccountDetailsPortalUrl, viewAccountDetailsLinkMessage, "vatAccountDetailsHref", sso = true),
        LinkMessage("/vat/makeAPayment", makeAPaymentLinkMessage, "vatMakePaymentHref", sso = false),
        LinkMessage(vatFileAReturnPortalUrl, fileAReturnLinkMessage, "vatFileReturnHref", sso = true))
      accountSummaryView.status shouldBe SummaryStatus.success
    }

    "return an error message if the account summary is not available" in {

      val accountSummary = VatAccountSummary(Some(VatAccountBalance(None)), Some(aDate))
      val mockVatConnector = mock[VatConnector]
      when(mockVatConnector.accountSummary(s"/vat/${vrn.vrn}")).thenReturn(Future.successful(Some(accountSummary)))
      val builder: VatAccountSummaryBuilder = VatAccountSummaryBuilder(mockVatConnector)
      val accountSummaryViewOption = builder.build(buildPortalUrl, userEnrolledForVat)
      accountSummaryViewOption shouldNot be(None)
      val accountSummaryView = accountSummaryViewOption.get
      accountSummaryView.regimeName shouldBe vatRegimeNameMessage
      accountSummaryView.messages shouldBe Seq[Msg](Msg(vatRegistrationNumberMessage, Seq(vrn.vrn)), Msg(vatSummaryUnavailableErrorMessage1), Msg(vatSummaryUnavailableErrorMessage2),
        Msg(vatSummaryUnavailableErrorMessage3), Msg(vatSummaryUnavailableErrorMessage4))
      accountSummaryView.addenda shouldBe Seq.empty
      accountSummaryView.status shouldBe SummaryStatus.default
    }

//    "return the oops summary if there is an exception when requesting the root" in {
//      val regimeRoots = RegimeRoots(vat = Some(new NumberFormatException)))
//      val user = User("tim", userAuthorityWithVrn, regimeRoots, None, None)
//      val mockVatConnector = mock[VatConnector]
//      val builder = new VatAccountSummaryBuilder(mockVatConnector)
//      val accountSummaryOption: Option[AccountSummary] = builder.build(buildPortalUrl, user)
//      accountSummaryOption should not be None
//      val accountSummary = accountSummaryOption.get
//      accountSummary.regimeName shouldBe vatRegimeNameMessage
//      accountSummary.messages shouldBe Seq[Msg](Msg(oopsMessage , Seq.empty))
//      accountSummary.addenda shouldBe Seq.empty
//      accountSummary.status shouldBe SummaryStatus.oops
//      verifyZeroInteractions(mockVatConnector)
//    }

    "return the oops summary if there i s an exception when requesting the account summary" in {
      val mockVatConnector = mock[VatConnector]
      when(mockVatConnector.accountSummary(s"/vat/$vrn/account-summary")).thenThrow(new NumberFormatException)
      val builder = new VatAccountSummaryBuilder(mockVatConnector)
      val accountSummaryOption: Option[Future[AccountSummary]] = builder.build(buildPortalUrl, userEnrolledForVat)
      accountSummaryOption should not be None
      val accountSummary = accountSummaryOption.get
      accountSummary.regimeName shouldBe vatRegimeNameMessage
      accountSummary.messages shouldBe Seq[Msg](Msg(oopsMessage , Seq.empty))
      accountSummary.addenda shouldBe Seq.empty
      accountSummary.status shouldBe SummaryStatus.oops
    }

    "return None if the user is not enrolled for VAT" in {

      val regimeRoots = RegimeRoots(None, None, None, None, None)
      val user = User("jim", ctAuthority("jim", "123"), regimeRoots, None, None)
      val mockVatConnector = mock[VatConnector]
      val builder: VatAccountSummaryBuilder = VatAccountSummaryBuilder(mockVatConnector)
      val accountSummaryViewOption = builder.build(buildPortalUrl, user)
      accountSummaryViewOption shouldBe None
    }
  }
}
