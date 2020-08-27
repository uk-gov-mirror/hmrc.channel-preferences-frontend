/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package controllers.internal

import connectors.GenericTerms
import model.PageType
import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec

class ReadOnlyAndroidOptOutPage24CohortSpec extends PlaySpec {

  "AndroidOptOut24 OptInCohort" should {
    "never change fields values " in {
      val cohortUnderTest = AndroidOptOutPage24

      withClue("id") {
        cohortUnderTest.id mustBe (24)
      }
      withClue("name") {
        cohortUnderTest.name mustBe ("AndroidOptOutPage24")
      }
      withClue("terms") {
        cohortUnderTest.terms mustBe (GenericTerms)
      }
      withClue("pageType") {
        cohortUnderTest.pageType mustBe (PageType.AndroidOptOutPage)
      }
      withClue("majorVersion") {
        cohortUnderTest.majorVersion mustBe (1)
      }
      withClue("minorVersion") {
        cohortUnderTest.minorVersion mustBe (2)
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
