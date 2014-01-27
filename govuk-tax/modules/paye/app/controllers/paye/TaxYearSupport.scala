package controllers.paye

import uk.gov.hmrc.utils.TaxYearResolver

trait TaxYearSupport {

  implicit def currentTaxYear = 2015//TMP TaxYearResolver.currentTaxYear
  implicit def taxYearInterval = TaxYearResolver.taxYearInterval
  implicit def currentTaxYearYearsRange = TaxYearResolver.currentTaxYearYearsRange
  implicit def startOfCurrentTaxYear = TaxYearResolver.startOfCurrentTaxYear
  implicit def endOfCurrentTaxYear = TaxYearResolver.endOfCurrentTaxYear

}
