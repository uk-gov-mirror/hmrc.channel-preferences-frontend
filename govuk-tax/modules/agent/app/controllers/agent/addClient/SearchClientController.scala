package controllers.agent.addClient

import uk.gov.hmrc.common.microservice.paye.domain.PayeRegime
import play.api.mvc.{ Result, Request }
import views.html.agents.addClient._
import controllers.common.{ SessionTimeoutWrapper, ActionWrappers, BaseController }
import play.api.data.{Form, Forms}
import Forms._
import org.joda.time.LocalDate
import controllers.common.validators.Validators
import uk.gov.hmrc.common.microservice.keystore.KeyStoreMicroService
import models.agent.addClient.{PreferredContact, AddClient, ClientSearch}
import scala.Some
import uk.gov.hmrc.common.microservice.domain.User
import controllers.common.service.MicroServices
import uk.gov.hmrc.common.microservice.agent.{MatchingPerson, SearchRequest, AgentMicroServices}
import uk.gov.hmrc.utils.DateConverter
import uk.gov.hmrc.common.microservice.agent.AgentRegime

class SearchClientController(keyStore: KeyStoreMicroService) extends BaseController
                                                                with ActionWrappers
                                                                with SessionTimeoutWrapper
                                                                with Validators {
  import SearchClientController.Validation._
  import SearchClientController.KeyStoreKeys._
  import SearchClientController.FieldIds._

  def this() = this(MicroServices.keyStoreMicroService)

  def start = WithSessionTimeoutValidation { AuthorisedForIdaAction(Some(AgentRegime)) { homeAction } }
  def search = WithSessionTimeoutValidation { AuthorisedForIdaAction(Some(AgentRegime)) { searchAction } }
  def add = WithSessionTimeoutValidation { AuthorisedForIdaAction(Some(AgentRegime)) { addAction } }
  def preferredContact = WithSessionTimeoutValidation { AuthorisedForIdaAction(Some(PayeRegime)) { preferredContactAction } }

  private[agent] def homeAction(user: User)(request: Request[_]): Result = {
    Ok(search_client(validDobRange, searchForm(request)))
  }

  private def unValidatedSearchForm = Form[ClientSearch](
    mapping(
      nino -> text,
      firstName -> optional(text),
      lastName -> optional(text),
      dob -> dateTuple
    )(ClientSearch.apply)(ClientSearch.unapply)
  )

  private def searchForm(request: Request[_]) = Form[ClientSearch](
    mapping(
      nino -> text.verifying("You must provide a valid nino", validateNino _),
      firstName -> optional(text).verifying("Invalid firstname", validateName _),
      lastName -> optional(text).verifying("Invalid last name", validateName _),
      dob -> dateTuple.verifying("Invalid date of birth", validateDob)
    ) (ClientSearch.apply)(ClientSearch.unapply).verifying("nino and at least two others must be filled in", (_) => atLeastTwoOptionalAndAllMandatory(unValidatedSearchForm.bindFromRequest()(request).get))
  )

  val validDobRange = {
    val thisYear = LocalDate.now().getYear
    (thisYear - 110) to (thisYear - 16)
  }
  private[agent] def searchAction(user: User)(request: Request[_]): Result = {
    val form = searchForm(request).bindFromRequest()(request)
    form.fold(
      errors => BadRequest(search_client(validDobRange, errors)),
      search => {
        val searchDob = search.dob.map(data => DateConverter.formatToString(data))
        agentMicroService.searchClient(SearchRequest(search.nino, search.firstName, search.lastName, searchDob)) match {
          case Some(result) => {
            val restrictedResult = MatchingPerson(result.nino,
                                                  search.firstName.flatMap(_ => result.firstName),
                                                  search.lastName.flatMap(_ => result.lastName),
                                                  search.dob.flatMap(_ => result.dateOfBirth))
            keyStore.addKeyStoreEntry(keystoreId(user.oid), serviceSourceKey, clientSearchObjectKey, restrictedResult)
            Ok(search_client_result(restrictedResult, addClientForm(request)))
          }
          case None => NotFound(search_client(validDobRange, form.withGlobalError("No match found")))
        }
      }
    )
  }

  private def addClientForm(request: Request[_]) = Form[AddClient](
    mapping(
      "correctClient" -> checked("You must check"),
      "authorised" -> checked("tou must check"),
      "internalClientReference" -> nonEmptyText()
    ) (AddClient.apply)(AddClient.unapply)
  )

  private val contactMapping = mapping(
    "pointOfContact" -> text,
    "contactName" -> text,
    "contactPhone" -> text,
    "contactEmail" -> text
  )(PreferredContact.apply)(PreferredContact.unapply)
  private def unValidatedPreferredContactForm(request: Request[_]) = Form[PreferredContact](
    contactMapping
  )

  private def preferredContactForm(request: Request[_]) = Form[PreferredContact](
    mapping(
      "pointOfContact" -> text,
      "contactName" -> text.verifying("Name is required",
        verifyContactName(_, unValidatedPreferredContactForm(request).bindFromRequest()(request).get)),
      "contactPhone" -> text.verifying("Phone is required",
        verifyContactName(_, unValidatedPreferredContactForm(request).bindFromRequest()(request).get)),
      "contactEmail" -> text.verifying("Email is required",
        verifyContactName(_, unValidatedPreferredContactForm(request).bindFromRequest()(request).get))
    ) (PreferredContact.apply)(PreferredContact.unapply)
  )

  private[addClient] def verifyContactName(name:String, preferredContact:PreferredContact) = {
    preferredContact.pointOfContact match {
      case "me" => true
      case "other" => false
      case "notUs" => true
      case _ => false // unknown situation
    }
  }

  private[addClient] def verifyContactPhone(name:String, preferredContact:PreferredContact) = {
    preferredContact.pointOfContact match {
      case "me" => true
      case "other" => false
      case "notUs" => true
      case _ => false // unknown situation
    }
  }

  private[addClient] def verifyContactEmail(name:String, preferredContact:PreferredContact) = {
    preferredContact.pointOfContact match {
      case "me" => true
      case "other" => false
      case "notUs" => true
      case _ => false // unknown situation
    }
  }

  private[agent] def addAction(user: User)(request: Request[_]): Result = {
    val searchedUser = keyStore.getEntry[MatchingPerson](keystoreId(user.oid), serviceSourceKey, clientSearchObjectKey)

    searchedUser match {
      case Some(u) => {
        val form = addClientForm(request).bindFromRequest()(request)
        if (form.hasErrors) {
          Ok(search_client_result(u, form))
        } else {
          Ok(search_client_preferred_contact(preferredContactForm(request)))
        }
      }
      case None => BadRequest("Requested to add a user but none has been selected")
    }
  }

  private[agent] def preferredContactAction(user: User)(request: Request[_]): Result = {
    val form = preferredContactForm(request).bindFromRequest()(request)

    if (form.hasErrors) {
      BadRequest(search_client_preferred_contact(form))
    }  else {
      Ok("you have added a client!")
    }
  }
}

object SearchClientController {

  private[addClient] object Validation {
    val nameRegex = """^[\p{L}\s'.-[0-9]]*"""

    val validateDob: Option[LocalDate] => Boolean = {
      case Some(dob) => dob.isBefore(LocalDate.now.minusYears(16).plusDays(1)) && dob.isAfter(LocalDate.now.minusYears(110).minusDays(1))
      case None => true
    }

    private[addClient] def validateName(s: Option[String]) = s.getOrElse("").trim.matches(nameRegex)

    private[addClient] def atLeastTwoOptionalAndAllMandatory(clientSearchNonValidated: ClientSearch) = {
      val items = List(clientSearchNonValidated.firstName.getOrElse("").trim.length > 0,
          clientSearchNonValidated.lastName.getOrElse("").trim.length > 0,
          clientSearchNonValidated.dob.isDefined)

      val count = items.foldLeft(0)((sum, valid) => if (valid) sum + 1 else sum)
      count >= 2 && validateNino(clientSearchNonValidated.nino)
    }

    def validateNino(s: String):Boolean =  {
      if (s == null)
        return false

      val startsWithNaughtyCharacters = List("BG", "GB", "NK", "KN", "TN", "NT", "ZZ").foldLeft(false) {
        ((found,stringVal) => found || s.startsWith(stringVal))
      }
      def validNinoFormat = s.matches("[[A-Z]&&[^DFIQUV]][[A-Z]&&[^DFIQUVO]] ?\\d{2} ?\\d{2} ?\\d{2} ?[A-Z]{1}")
      !startsWithNaughtyCharacters && validNinoFormat
    }
  }

  private[addClient] object KeyStoreKeys {
    val serviceSourceKey = "agentFrontEnd"
    def keystoreId(id: String) = s"AddClient:$id"
    val clientSearchObjectKey = "clientSearchObject"
  }

  private[addClient] object FieldIds {
    val nino = "nino";
    val firstName = "firstName";
    val lastName = "lastName";
    val dob = "dob";
  }
}

