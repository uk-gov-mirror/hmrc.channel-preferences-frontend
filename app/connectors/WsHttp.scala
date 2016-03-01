package connectors

import config.Audit
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.{AppName, RunMode}
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost, WSPut}


object WsHttp extends WSGet with WSPut with WSPost with AppName with HttpAuditing with RunMode {
  override val hooks = Seq(AuditingHook)
  override val auditConnector: AuditConnector = Audit
}