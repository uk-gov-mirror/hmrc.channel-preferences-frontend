package uk.gov.hmrc.common.microservice.agent

import uk.gov.hmrc.microservice._
import play.api.libs.json.Json
import controllers.common.domain.Transform._
import uk.gov.hmrc.domain.Uar
import play.api.libs.ws.Response
import controllers.common.actions.HeaderCarrier

class AgentConnectorRoot(override val serviceUrl: String = MicroServiceConfig.agentServiceUrl) extends Connector {

  def root(uri: String)(implicit hc: HeaderCarrier) = httpGetHC[AgentRoot](uri).getOrElse(throw new IllegalStateException(s"Expected Agent root not found at URI '$uri'"))

}
