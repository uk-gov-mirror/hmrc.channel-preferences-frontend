package uk.gov.hmrc.microservice.sa

import play.Logger
import uk.gov.hmrc.microservice.{ MicroServiceException, MicroService, MicroServiceConfig }
import play.api.libs.json.Json
import controllers.common.domain.Transform._
import uk.gov.hmrc.microservice.sa.domain.{ SaAccountSummary, SaPerson, SaRoot, TransactionId }
import uk.gov.hmrc.common.microservice.sa.domain.write.SaAddressForUpdate

class SaMicroService extends MicroService {

  override val serviceUrl = MicroServiceConfig.saServiceUrl

  def root(uri: String): SaRoot = httpGet[SaRoot](uri).getOrElse(throw new IllegalStateException(s"Expected SA root not found at URI '$uri'"))

  def person(uri: String): Option[SaPerson] = httpGet[SaPerson](uri)

  def accountSummary(uri: String): Option[SaAccountSummary] = httpGet[SaAccountSummary](uri)

  def updateMainAddress(uri: String, mainAddress: SaAddressForUpdate): Either[String, TransactionId] = {

    val response = httpPostSynchronous(uri, Json.parse(toRequestBody(mainAddress)))

    response.status match {
      case 202 => Right(extractJSONResponse[TransactionId](response))
      case 409 => Left("A previous details change is already being processed, this will take up to 48 hours to complete.")
      case _ => throw new MicroServiceException("Error updating main address", response)
    }
  }
}
