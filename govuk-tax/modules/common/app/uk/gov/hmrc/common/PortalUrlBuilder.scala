package uk.gov.hmrc.common

import config.PortalConfig
import play.api.mvc.Request
import play.api.Logger
import uk.gov.hmrc.common.microservice.domain.User
import uk.gov.hmrc.utils.TaxYearResolver

trait PortalUrlBuilder extends AffinityGroupParser {

  def buildPortalUrl(destinationPathKey: String)(implicit request: Request[AnyRef], user: User): String = {
    val currentTaxYear = TaxYearResolver.currentTaxYear
    val saUtr = user.userAuthority.saUtr
    val vrn = user.userAuthority.vrn
    val ctUtr = user.userAuthority.ctUtr
    val affinityGroup = parseAffinityGroup
    val destinationUrl = PortalConfig.getDestinationUrl(destinationPathKey)
    val tagsToBeReplacedWithData = Seq(
      ("<year>", Some(currentTaxYear)),
      ("<utr>", saUtr),
      ("<affinitygroup>", Some(affinityGroup)),
      ("<vrn>", vrn), ("<ctutr>", ctUtr)
    )

    resolvePlaceHolder(destinationUrl, tagsToBeReplacedWithData)
  }


  private def resolvePlaceHolder(url: String, tagsToBeReplacedWithData: Seq[(String, Option[Any])]): String =
    if (tagsToBeReplacedWithData.isEmpty)
      url
    else
      resolvePlaceHolder(replace(url, tagsToBeReplacedWithData.head), tagsToBeReplacedWithData.tail)

  private def replace(url: String, tagToBeReplacedWithData: (String, Option[Any])): String = {
    val (tagName, tagValueOption) = tagToBeReplacedWithData
    tagValueOption match {
      case Some(valueOfTag) => url.replace(tagName, valueOfTag.toString)
      case _ => {
        if (url.contains(tagName)) {
          Logger.error(s"Failed to populate parameter $tagName in URL $url")
        }
        url
      }
    }
  }
}

