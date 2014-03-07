package controllers.common.service

import uk.gov.hmrc.common.microservice.deskpro.HmrcDeskproConnector
import uk.gov.hmrc.common.microservice.txqueue.TxQueueConnector
import uk.gov.hmrc.common.microservice.auth.AuthConnector
import uk.gov.hmrc.common.microservice.paye.PayeConnector
import uk.gov.hmrc.common.microservice.audit.AuditConnector
import uk.gov.hmrc.common.microservice.keystore.KeyStoreConnector
import uk.gov.hmrc.common.microservice.vat.VatConnector
import uk.gov.hmrc.common.microservice.saml.SamlConnector
import uk.gov.hmrc.common.microservice.epaye.EpayeConnector
import uk.gov.hmrc.common.microservice.ct.CtConnector
import uk.gov.hmrc.common.microservice.sa.SaConnector
import uk.gov.hmrc.common.microservice.governmentgateway.GovernmentGatewayConnector
import uk.gov.hmrc.common.microservice.preferences.PreferencesConnector
import uk.gov.hmrc.common.microservice.email.EmailConnector

trait ConnectorsApi {
  val authConnector: AuthConnector
  val payeConnector: PayeConnector
  val samlConnector: SamlConnector
  val saConnector: SaConnector
  val governmentGatewayConnector: GovernmentGatewayConnector
  val txQueueConnector: TxQueueConnector
  val auditConnector: AuditConnector
  val keyStoreConnector: KeyStoreConnector
  val vatConnector: VatConnector
  val ctConnector: CtConnector
  val epayeConnector: EpayeConnector
  val preferencesConnector: PreferencesConnector
  val emailConnector: EmailConnector
  val hmrcDeskproConnector: HmrcDeskproConnector
}

object Connectors extends ConnectorsApi {

  implicit lazy val authConnector = new AuthConnector()
  implicit lazy val payeConnector = new PayeConnector()
  implicit lazy val samlConnector = new SamlConnector()
  implicit lazy val saConnector = new SaConnector()
  implicit lazy val governmentGatewayConnector = new GovernmentGatewayConnector()
  implicit lazy val txQueueConnector = new TxQueueConnector()
  implicit lazy val auditConnector = new AuditConnector()
  implicit lazy val keyStoreConnector = new KeyStoreConnector()
  implicit lazy val vatConnector = new VatConnector()
  implicit lazy val ctConnector = new CtConnector()
  implicit lazy val epayeConnector = new EpayeConnector()
  implicit lazy val preferencesConnector = new PreferencesConnector()
  implicit lazy val emailConnector = new EmailConnector()
  implicit lazy val hmrcDeskproConnector = new HmrcDeskproConnector()
}