import com.github.tomakehurst.wiremock.client.WireMock._
import conf.ServerSetup
import org.scalatest.concurrent.ScalaFutures
import pages._
import uk.gov.hmrc.endtoend
import uk.gov.hmrc.endtoend.sa.config.UserWithUtr

class UpgradePageBrowserSpec extends endtoend.sa.Spec with ScalaFutures with ServerSetup {
  import stubs._

  implicit val user = new UserWithUtr { val utr = "1111111111" }

  feature("The generic upgrade page") {
    scenario("Agree to upgrading") {
      Given("I am logged in")
        go to Auth.loginPage

      And("I have my preferences set")
        givenThat (Auth.`GET /auth/authority` willReturn (aResponse withStatus 200 withBody Auth.authorityRecordJson))
        givenThat (Preferences.`GET /preferences/sa/individual/<utr>/print-suppression` willReturn (
          aResponse withStatus 200 withBody Preferences.optedInPreferenceJson
        ))

      When("I go to the Upgrade Page")
        val upgradePage = GenericUpgradePage(returnUrl = Host.ReturnPage)
        go to upgradePage
        upgradePage should be (displayed)

      When("I click 'Yes' and then 'Submit")
        givenThat(Preferences.`POST /preferences/sa/individual/<utr>/terms-and-conditions` willReturn (aResponse withStatus 200))
        click on upgradePage.`terms and conditions checkbox`
        click on upgradePage.`continue`

      Then("I am taken to the you're signed up page")
        val genericUpgradeConfirmationPage = GenericUpgradeConfirmationPage(Host.ReturnPage)
        genericUpgradeConfirmationPage should be (displayed)

      When("I click on continue button")
        click on genericUpgradeConfirmationPage.`continue button`

      Then("I am taken back to the return page")
        Host.ReturnPage should be (displayed)

      And("My T&Cs have been set to generic=accepted")
        verify(Preferences.`POST /preferences/sa/individual/<utr>/terms-and-conditions`(genericAccepted = true))
    }
  }
}


