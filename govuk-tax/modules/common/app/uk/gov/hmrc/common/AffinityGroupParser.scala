package uk.gov.hmrc.common

import play.api.mvc.Request
import controllers.common.SessionKeys
import play.api.Logger

trait AffinityGroupParser {

  def parseAffinityGroup(implicit request: Request[AnyRef]): String =
    request.session.get(SessionKeys.affinityGroup).getOrElse {
      Logger.error("Affinity Group not found")
      throw new RuntimeException("Affinity Group not found")
    }
}
