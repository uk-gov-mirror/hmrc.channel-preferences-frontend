package controllers.common

import service.Encryption
import play.api.libs.json._
import play.api.mvc.{AnyContent, Request, Action}
import config.PortalConfig
import play.api.{Logger, Play}

object SsoPayloadEncryptor extends Encryption {
  val encryptionKey = Play.current.configuration.getString("sso.encryption.key").get
}

class SsoOutController extends BaseController with ActionWrappers with CookieEncryption with SessionTimeoutWrapper {

  def encryptPayload = WithSessionTimeoutValidation(Action(BadRequest("Error")), Action {
    implicit request =>

      if (requestValid(request)) {
        val destinationUrl = retriveDestinationUrl
        val decryptedEncodedGovernmentGatewayToken = decrypt(request.session.get("token").get)
        val encryptedPayload = SsoPayloadEncryptor.encrypt(generateJsonPayload(decryptedEncodedGovernmentGatewayToken, destinationUrl))
        Ok(encryptedPayload)
      } else {
        BadRequest("Error")
      }
  })

  private def retriveDestinationUrl(implicit request: Request[AnyContent]): String = {
    request.queryString.get("destinationUrl") match {
      case Some(Seq(destination)) => destination
      case None => PortalConfig.getDestinationUrl("home")
    }
  }

  private def generateJsonPayload(token: String, dest: String) = {
    Json.stringify(Json.obj(("gw", token), ("dest", dest), ("time", now().getMillis)))
  }

  private def requestValid(request: Request[AnyContent]): Boolean = {

    def theDestinationIsInvalid = {
      request.queryString.get("destinationUrl") match {
        case Some(Seq(destination: String)) => destination match {
          case d if d.startsWith(PortalConfig.destinationRoot) => false
          case _ => {
            Logger.error(s"Host of Single Sign On destination URL $destination is not in the white list")
            true
          }
        }
        case None => false
        case Some(destinations: Seq[String]) => {
          Logger.error(s"Single Sign On was attempted with multilple destination URLs : ${destinations.mkString(", ")}")
          true
        }
      }
    }
    def theTokenIsMissing = {
      request.session.get("token") match {
        case Some(_) => false
        case _ => {
          Logger.error("Single Sign On was attempted without a valid government gateway token")
          true
        }
      }
    }
    !theTokenIsMissing && !theDestinationIsInvalid
  }
}
