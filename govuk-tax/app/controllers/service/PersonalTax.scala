package controllers.service

import java.net.URI

case class PayeData(nino: String, firstName: String, lastName: String, links: Map[String, URI])

case class EmploymentData(name: String)

case class Employments(employments: List[EmploymentData])

case class Benefit(taxYear: String, grossAmount: Double)

case class SelfAssessmentData(returns: Option[URI])

// .. etc

class PersonalTax(val personal: Personal = new Personal()) extends ResponseHandler {

  import scala.concurrent.Future

  def payeData(uri: String): Future[PayeData] = response[PayeData](personal.resource(uri).get)

  def saData(uri: String): Future[SelfAssessmentData] = response[SelfAssessmentData](personal.resource(uri).get)

  def employments(uri: String): Future[Employments] = response[Employments](personal.resource(uri).get)

  def benefits(uri: String): Future[List[Benefit]] = response[List[Benefit]](personal.resource(uri).get)
}

