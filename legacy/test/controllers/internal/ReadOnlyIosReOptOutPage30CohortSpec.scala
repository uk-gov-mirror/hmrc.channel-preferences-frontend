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

package controllers.internal

import connectors.GenericTerms
import model.PageType
import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec

class ReadOnlyIosReOptOutPage30CohortSpec extends PlaySpec {

  "IosReOptOut30 OptInCohort" should {
    "never change fields values " in {
      val cohortUnderTest = IosReOptOutPage30

      withClue("id") {
        cohortUnderTest.id mustBe (30)
      }
      withClue("name") {
        cohortUnderTest.name mustBe ("IosReOptOutPage30")
      }
      withClue("terms") {
        cohortUnderTest.terms mustBe (GenericTerms)
      }
      withClue("pageType") {
        cohortUnderTest.pageType mustBe (PageType.IosReOptOutPage)
      }
      withClue("majorVersion") {
        cohortUnderTest.majorVersion mustBe (0)
      }
      withClue("minorVersion") {
        cohortUnderTest.minorVersion mustBe (0)
      }
      withClue("description") {
        cohortUnderTest.description mustBe ("")
      }
      withClue("date") {
        cohortUnderTest.date mustBe new LocalDate("2020-01-01")
      }
    }
  }

}
