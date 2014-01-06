package uk.gov.hmrc.common.microservice.keystore

import uk.gov.hmrc.common.microservice.{MicroServiceConfig, Connector}
import org.joda.time.DateTime
import controllers.common.actions.HeaderCarrier
import scala.concurrent._
import ExecutionContext.Implicits.global

case class KeyStore[T](id: String, dateCreated: DateTime, dateUpdated: DateTime, data: Map[String, T]) {
  def get(key: String): Option[T] = {
    data.get(key)
  }
}

class KeyStoreConnector(override val serviceUrl: String = MicroServiceConfig.keyStoreServiceUrl) extends Connector {

  def addKeyStoreEntry[T](actionId: String, source: String, formId: String, data: T, ignoreSession: Boolean = false)(implicit manifest: Manifest[T], headerCarrier: HeaderCarrier) = {
    val keyStoreId = generateKeyStoreId(actionId, ignoreSession)
    val uri = buildUri(keyStoreId, source) + s"/data/$formId"
    httpPutF[T, KeyStore[T]](uri, data)
  }

  def getEntry[T](actionId: String, source: String, formId: String, ignoreSession: Boolean = false)
                 (implicit manifest: Manifest[T], hc: HeaderCarrier): Future[Option[T]] = {
    val keyStoreId = generateKeyStoreId(actionId, ignoreSession)
    httpGetF[KeyStore[T]](buildUri(keyStoreId, source)).map {
      maybeKeyStore =>
        for {
          keyStore <- maybeKeyStore
          value <- keyStore.get(formId)
        } yield value
    }
  }

  def getKeyStore[T](actionId: String, source: String, ignoreSession: Boolean = false)
                    (implicit manifest: Manifest[T], hc: HeaderCarrier): Future[Option[KeyStore[T]]] = {
    val keyStoreId = generateKeyStoreId(actionId, ignoreSession)
    httpGetF[KeyStore[T]](buildUri(keyStoreId, source))
  }

  def deleteKeyStore(actionId: String, source: String, ignoreSession: Boolean = false)(implicit hc: HeaderCarrier) {
    val keyStoreId = generateKeyStoreId(actionId, ignoreSession)
    httpDeleteAndForget(buildUri(keyStoreId, source))
  }

  def getDataKeys(actionId: String, source: String, ignoreSession: Boolean = false)(implicit hc: HeaderCarrier): Future[Option[Set[String]]] = {
    val keyStoreId = generateKeyStoreId(actionId, ignoreSession)
    httpGetF[Set[String]](buildUri(keyStoreId, source) + "/data/keys")
  }

  private def buildUri(id: String, source: String) = s"/keystore/$source/$id"

  private def generateKeyStoreId(actionId: String, ignoreSession: Boolean)(implicit headerCarrier: HeaderCarrier) = {
    val userId = headerCarrier.userId.map { userId => userId.substring(userId.lastIndexOf("/") + 1)}.getOrElse("unknownUserId")
    val sessionId = generateSessionIdForKeyStoreId(ignoreSession)
    s"$userId:$actionId$sessionId"
  }

  private def generateSessionIdForKeyStoreId(ignoreSession: Boolean)(implicit headerCarrier: HeaderCarrier) = {
    if (ignoreSession == false) {
      val sessionId = headerCarrier.sessionId.getOrElse("unknownSessionId")
      s":$sessionId"
    }
    else ""
  }

}
