package views

import _root_.helpers.{ConfigHelper, WelshLanguage}
import controllers.auth.AuthenticatedRequest
import org.jsoup.Jsoup
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.i18n.Messages.Implicits.applicationMessages
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.emailaddress.ObfuscatedEmailAddress
import uk.gov.hmrc.play.test.UnitSpec
import views.html.main

class MainSpec extends UnitSpec with OneAppPerSuite with WelshLanguage {

  override implicit lazy val app: Application = ConfigHelper.fakeApp

  "main template" should {
    "render the correct content in english" in {
      val email = ObfuscatedEmailAddress("a@a.com")
      val document = Jsoup.parse(main("title")(Html("Some HTML"))(AuthenticatedRequest(FakeRequest("GET", "/"), None, None, None, None), applicationMessages).toString())

      document.getElementsByClass("header__menu__proposition-name").get(0).text() shouldBe "Your tax account"
    }

    "render the correct content in welsh" in {
      val email = ObfuscatedEmailAddress("a@a.com")
      val document = Jsoup.parse(main("title")(Html("Some HTML"))(welshRequest, messagesInWelsh(applicationMessages)).toString())

      document.getElementsByClass("header__menu__proposition-name").get(0).text() shouldBe "Eich cyfrif treth"
    }
  }
}
