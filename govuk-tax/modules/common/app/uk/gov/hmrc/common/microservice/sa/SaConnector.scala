package uk.gov.hmrc.common.microservice.sa

import uk.gov.hmrc.microservice.{ Connector, MicroServiceConfig }
import play.api.libs.json.Json
import controllers.common.domain.Transform._
import uk.gov.hmrc.common.microservice.sa.domain.write.{TransactionId, SaAddressForUpdate}
import uk.gov.hmrc.microservice.MicroServiceException
import uk.gov.hmrc.common.microservice.sa.domain.{SaAccountSummary, SaPerson, SaJsonRoot}
import scala.concurrent.Future
import controllers.common.actions.HeaderCarrier

class SaConnector extends Connector {

  override val serviceUrl = MicroServiceConfig.saServiceUrl

  def root(uri: String)(implicit hc: HeaderCarrier): SaJsonRoot = httpGetHC[SaJsonRoot](uri).getOrElse(SaJsonRoot(Map.empty))

  def person(uri: String)(implicit hc: HeaderCarrier): Option[SaPerson] = httpGetHC[SaPerson](uri)

  def accountSummary(uri: String)(implicit headerCarrier:HeaderCarrier): Future[Option[SaAccountSummary]] = httpGetF[SaAccountSummary](uri)

  def updateMainAddress(uri: String, mainAddress: SaAddressForUpdate): Either[String, TransactionId] = {

    val response = httpPostSynchronous(uri, Json.parse(toRequestBody(mainAddress)))

    response.status match {
      case 202 => Right(extractJSONResponse[TransactionId](response))
      case 409 => Left("A previous details change is already being processed, this will take up to 48 hours to complete.")
      case _ => throw new MicroServiceException("Error updating main address", response)
    }
  }
}
