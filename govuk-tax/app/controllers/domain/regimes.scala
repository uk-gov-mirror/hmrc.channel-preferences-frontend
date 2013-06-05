package controllers.domain

import microservice.auth.domain.MatsUserAuthority

sealed abstract class TaxRegime

class PayeRegime extends TaxRegime

case class PayeDesignatoryDetails(name: String)

sealed abstract class RegimeRoot

case class PayeRoot(designatoryDetails: PayeDesignatoryDetails,
    links: Map[String, String]) extends RegimeRoot {
}

case class User(userAuthority: MatsUserAuthority,
  regime: RegimeRoots)

case class RegimeRoots(paye: Option[PayeRoot])

