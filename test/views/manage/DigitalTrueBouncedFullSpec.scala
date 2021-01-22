/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package views.manage

import _root_.helpers.{ ConfigHelper, LanguageHelper, TestFixtures }
import connectors.EmailPreference
import controllers.auth.AuthenticatedRequest
import org.jsoup.Jsoup
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.test.FakeRequest
import views.html.manage._

class DigitalTrueBouncedFullSpec extends PlaySpec with GuiceOneAppPerSuite with LanguageHelper with ConfigHelper {

  override implicit lazy val app: Application = fakeApp
  val template = app.injector.instanceOf[digital_true_bounced_full]

  "settings page for digital true bounced" should {
    "render the correct content in english" in {
      val emailAddress = "a@a.com"
      val email = EmailPreference(emailAddress, true, true, false, None)

      val authRequest = AuthenticatedRequest(FakeRequest(), None, None, None, None)

      val document =
        Jsoup.parse(template(email)(authRequest, messagesInEnglish(), TestFixtures.sampleHostContext).toString())
      document.getElementsByClass("govuk-link").get(2).attr("href") must be(
        "/paperless/email-address/delivery-failed?returnUrl=kvXgJfoJJ%2FbmaHgdHhhRpg%3D%3D&returnLinkText=huhgy5odc6KaXfFIMZXkeZjs11wvNGxKPz2CtY8L8GM%3D")
      document.getElementById("saCheckSettings").text() mustBe "Check your settings"
    }

    "render the correct content in welsh" in {
      val emailAddress = "a@a.com"
      val email = EmailPreference(emailAddress, true, true, false, None)
      val document =
        Jsoup.parse(template(email)(welshRequest, messagesInWelsh(), TestFixtures.sampleHostContext).toString())

      document.getElementById("saCheckSettings").text() mustBe "Gwirio’ch gosodiadau"
    }
  }
}
