/*
 * Copyright 2019 HM Revenue & Customs
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

import java.util.UUID

import play.api.test.Helpers._

class ActivateISpec extends EmailSupport {

  "activate" should {
    "return PRECONDITION_FAILED with redirectUserTo link if activating for a new user with utr only" in {
      val response = `/paperless/activate`(utr)().put().futureValue
      response.status must be(PRECONDITION_FAILED)
      (response.json \ "redirectUserTo").as[String] must be(
        s"http://localhost:9024/paperless/choose?returnUrl=$encryptedReturnUrl&returnLinkText=$encryptedReturnText")
      (response.json \ "optedIn").asOpt[Boolean] mustBe empty
      (response.json \ "verifiedEmail").asOpt[Boolean] mustBe empty
    }

    "return PRECONDITION_FAILED with redirectUserTo link if activating for a new user with nino only for taxCredits" in {
      val termsAndConditions = "taxCredits"
      val emailAddress = "test@test.com"
      val response = `/paperless/activate`(nino)(Some(termsAndConditions), Some(emailAddress)).put().futureValue
      response.status must be(PRECONDITION_FAILED)
      (response.json \ "redirectUserTo").as[String] must be(
        s"http://localhost:9024/paperless/choose?returnUrl=$encryptedReturnUrl&returnLinkText=$encryptedReturnText&termsAndConditions=${encryptAndEncode(
          termsAndConditions)}&email=${encryptAndEncode(emailAddress)}")
      (response.json \ "optedIn").asOpt[Boolean] mustBe empty
      (response.json \ "verifiedEmail").asOpt[Boolean] mustBe empty
    }

    "return BAD_REQUEST with activating for a new user with nino only for taxCredits without providing email" in {
      val response =
        `/paperless/activate`(nino)(termsAndConditions = Some("taxCredits"), emailAddress = None).put().futureValue
      response.status must be(BAD_REQUEST)
    }

    "return PRECONDITION_FAILED with redirectUserTo link if activating for a new user with given utr and nino" in {
      val response = `/paperless/activate`(nino, utr)().put().futureValue
      response.status must be(PRECONDITION_FAILED)
      (response.json \ "redirectUserTo").as[String] must be(
        s"http://localhost:9024/paperless/choose?returnUrl=$encryptedReturnUrl&returnLinkText=$encryptedReturnText")
      (response.json \ "optedIn").asOpt[Boolean] mustBe empty
      (response.json \ "verifiedEmail").asOpt[Boolean] mustBe empty
    }

    "return UNAUTHORIZED if activating for a user with no nino or utr" in {
      val response = `/paperless/activate`()().put().futureValue
      response.status must be(UNAUTHORIZED)
    }

    "return PRECONDITION_FAILED with redirectUserTo link if activating for a new user with nino only" in {
      val response = `/paperless/activate`(nino)().put().futureValue
      response.status must be(PRECONDITION_FAILED)
      (response.json \ "redirectUserTo").as[String] must be(
        s"http://localhost:9024/paperless/choose?returnUrl=$encryptedReturnUrl&returnLinkText=$encryptedReturnText")
      (response.json \ "optedIn").asOpt[Boolean] mustBe empty
      (response.json \ "verifiedEmail").asOpt[Boolean] mustBe empty
    }

    "return OK with the optedIn attribute set to true and verifiedEmail set to false if the user has opted in and not verified" in {

      val email = s"${UUID.randomUUID().toString}@email.com"
      1 must be(1)
      `/preferences/terms-and-conditions`(ggAuthHeaderWithUtr).postGenericOptIn(email).futureValue.status must be(201)

      val response = `/paperless/activate`(utr)().put().futureValue
      response.status must be(OK)
      (response.json \ "optedIn").as[Boolean] mustBe true
      (response.json \ "verifiedEmail").as[Boolean] mustBe false
      (response.json \ "redirectUserTo").asOpt[String] mustBe empty

    }

    "return OK with the optedIn attribute set to true and verifiedEmail set to true if the user has opted in and verified" in {

      val utr = Generate.utr
      val email = s"${UUID.randomUUID().toString}@email.com"
      `/preferences/terms-and-conditions`(authHelper.authHeader(utr))
        .postGenericOptIn(email)
        .futureValue
        .status must be(201)
      `/preferences-admin/sa/individual`.verifyEmailFor(`/entity-resolver/sa/:utr`(utr.value)).futureValue.status must be(
        204)
      val response = `/paperless/activate`(utr)().put().futureValue
      response.status must be(OK)
      (response.json \ "optedIn").as[Boolean] mustBe true
      (response.json \ "verifiedEmail").as[Boolean] mustBe true
      (response.json \ "redirectUserTo").asOpt[String] mustBe empty

    }

    "return OK with the optedId attribute set to false if the user has opted out" in {

      val utr = Generate.utr
      val email = s"${UUID.randomUUID().toString}@email.com"
      `/preferences/terms-and-conditions`(authHelper.authHeader(utr)).postGenericOptOut.futureValue.status must be(201)

      val response = `/paperless/activate`(utr)().put().futureValue
      response.status must be(OK)
      (response.json \ "optedIn").as[Boolean] mustBe false
      (response.json \ "verifiedEmail").asOpt[Boolean] mustBe empty
      (response.json \ "redirectUserTo").asOpt[String] mustBe empty

    }

    "return CONFLICT if trying to activate providing an email diffrent than the stored one" in {
      val originalEmail = "generic@test.com"
      `/preferences/terms-and-conditions`(ggAuthHeaderWithNino)
        .postGenericOptIn(originalEmail)
        .futureValue
        .status must be(CREATED)

      `/paperless/activate`(nino)(Some("taxCredits"), Some("taxCredits@test.com")).put().futureValue.status must be(
        CONFLICT)
    }
  }
}
