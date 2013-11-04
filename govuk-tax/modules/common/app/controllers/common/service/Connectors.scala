package controllers.common.service

import uk.gov.hmrc.common.microservice.txqueue.TxQueueConnector


@deprecated("this will eventually be removed. All new controllers should wire in the services that they require", "")
trait Connectors {

  import uk.gov.hmrc.common.microservice.auth.AuthConnector
  import uk.gov.hmrc.common.microservice.paye.PayeConnector
  import uk.gov.hmrc.common.microservice.audit.AuditConnector
  import uk.gov.hmrc.common.microservice.keystore.KeyStoreConnector
  import uk.gov.hmrc.common.microservice.agent.AgentConnectorRoot
  import uk.gov.hmrc.common.microservice.vat.VatConnector
  import uk.gov.hmrc.common.microservice.saml.SamlConnector
  import uk.gov.hmrc.common.microservice.epaye.EpayeConnector
  import uk.gov.hmrc.common.microservice.ct.CtConnector
  import uk.gov.hmrc.common.microservice.sa.SaConnector
  import uk.gov.hmrc.common.microservice.governmentgateway.GovernmentGatewayConnector

  implicit lazy val authConnector = new AuthConnector()
  implicit lazy val payeConnector = new PayeConnector()
  implicit lazy val samlConnector = new SamlConnector()
  implicit lazy val saConnector = new SaConnector()
  implicit lazy val governmentGatewayConnector = new GovernmentGatewayConnector()
  implicit lazy val txQueueConnector = new TxQueueConnector()
  implicit lazy val auditConnector = new AuditConnector()
  implicit lazy val keyStoreConnector = new KeyStoreConnector()
  implicit lazy val agentConnectorRoot = new AgentConnectorRoot()
  implicit lazy val vatConnector = new VatConnector()
  implicit lazy val ctConnector = new CtConnector()
  implicit lazy val epayeConnector = new EpayeConnector()

}

object Connectors extends Connectors