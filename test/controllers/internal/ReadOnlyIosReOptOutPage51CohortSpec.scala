/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package controllers.internal

import connectors.GenericTerms
import model.PageType
import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec

class ReadOnlyIosReOptOutPage51CohortSpec extends PlaySpec {

  "IosReOptOutPage51 OptInCohort" should {
    "never change fields values " in {
      val cohortUnderTest = IosReOptOutPage51

      withClue("id") {
        cohortUnderTest.id mustBe (51)
      }
      withClue("name") {
        cohortUnderTest.name mustBe ("IosReOptOutPage51")
      }
      withClue("terms") {
        cohortUnderTest.terms mustBe (GenericTerms)
      }
      withClue("pageType") {
        cohortUnderTest.pageType mustBe (PageType.IosReOptOutPage)
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