/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package controllers.internal

import connectors.GenericTerms
import model.PageType
import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec

class ReadOnlyAndroidReOptOutPage49CohortSpec extends PlaySpec {

  "AndroidReOptOutPage49 OptInCohort" should {
    "never change fields values " in {
      val cohortUnderTest = AndroidReOptOutPage49

      withClue("id") {
        cohortUnderTest.id mustBe (49)
      }
      withClue("name") {
        cohortUnderTest.name mustBe ("AndroidReOptOutPage49")
      }
      withClue("terms") {
        cohortUnderTest.terms mustBe (GenericTerms)
      }
      withClue("pageType") {
        cohortUnderTest.pageType mustBe (PageType.AndroidReOptOutPage)
      }
      withClue("majorVersion") {
        cohortUnderTest.majorVersion mustBe (1)
      }
      withClue("minorVersion") {
        cohortUnderTest.minorVersion mustBe (3)
      }
      withClue("description") {
        cohortUnderTest.description mustBe ("")
      }
      withClue("date") {
        cohortUnderTest.date mustBe new LocalDate("2020-08-01")
      }
    }
  }

}