package uk.gov.hmrc.common.microservice.domain

import uk.gov.hmrc.common.microservice.epaye.domain.EpayeDomain.EpayeRoot
import uk.gov.hmrc.common.microservice.ct.domain.CtDomain.CtRoot
import scala.util.Try
import uk.gov.hmrc.common.microservice.auth.domain.{Regimes, UserAuthority}
import uk.gov.hmrc.common.microservice.paye.domain.PayeRoot
import uk.gov.hmrc.common.microservice.vat.domain.VatDomain.VatRoot
import uk.gov.hmrc.common.microservice.sa.domain.SaRoot
import play.api.Logger
import org.apache.commons.lang.exception.ExceptionUtils
import uk.gov.hmrc.common.microservice.domain.RegimeRoots.RegimeRootBuilder

abstract class TaxRegime {

  def isAuthorised(regimes: Regimes): Boolean

  def unauthorisedLandingPage: String
}

abstract class RegimeRoot[I] {
  def identifier  : I
}

case class User(userId: String,
                userAuthority: UserAuthority,
                regimes: RegimeRoots,
                nameFromGovernmentGateway: Option[String] = None,
                decryptedToken: Option[String]) {

  def oid: String = userId.substring(userId.lastIndexOf("/") + 1)

}



class RegimeRoots(  payeBuilder : Option[RegimeRootBuilder[PayeRoot]],
                        saBuilder : Option[RegimeRootBuilder[SaRoot]],
                        vatBuilder : Option[RegimeRootBuilder[VatRoot]],
                        epayeBuilder : Option[RegimeRootBuilder[EpayeRoot]],
                        ctBuilder : Option[RegimeRootBuilder[CtRoot]]) {
  lazy val paye : Option[Try[PayeRoot]] = payeBuilder.map(_())
  lazy val sa : Option[Try[SaRoot]] = saBuilder.map(_())
  lazy val vat : Option[Try[VatRoot]] = vatBuilder.map(_())
  lazy val epaye : Option[Try[EpayeRoot]] = epayeBuilder.map(_())
  lazy val ct : Option[Try[CtRoot]] = ctBuilder.map(_())

  lazy val hasBusinessTaxRegime: Boolean = sa.isDefined || vat.isDefined || epaye.isDefined || ct.isDefined

  override def equals(that : Any) : Boolean = {
    that match {
      case regimeRoots : RegimeRoots => paye == regimeRoots.paye && sa == regimeRoots.sa && vat == regimeRoots.vat && epaye == regimeRoots.epaye && ct == regimeRoots.ct
      case _ => false
    }
  }
  override def hashCode : Int = paye.hashCode() + sa.hashCode() * 2 + vat.hashCode() * 3 + epaye.hashCode() * 5 + ct.hashCode() * 7
}

object RegimeRoots {
  def apply(paye: Option[Try[PayeRoot]] = None, sa : Option[Try[SaRoot]] = None,
            vat : Option[Try[VatRoot]] = None, epaye : Option[Try[EpayeRoot]] = None,
            ct : Option[Try[CtRoot]] = None) : RegimeRoots = {
    val (_paye, _sa, _vat, _epaye, _ct) = (paye, sa, vat, epaye, ct)
    new RegimeRoots(None, None, None, None, None) {
      override lazy val paye = _paye
      override lazy val sa = _sa
      override lazy val vat = _vat
      override lazy val epaye = _epaye
      override lazy val ct = _ct
    }
  }

  def unapply(root: RegimeRoots) : Option[(Option[Try[PayeRoot]], Option[Try[SaRoot]],
    Option[Try[VatRoot]], Option[Try[EpayeRoot]],
    Option[Try[CtRoot]])] = {
    Some((root.paye, root.sa, root.vat, root.epaye, root.ct))
  }

  case class RegimeRootBuilder[R] (build : () => R) {
    def apply() : Try[R] = {
      val result = Try(build())
      if (result.isFailure) Logger.error(s"Exception caught here but may be re-thrown later: ${ExceptionUtils.getFullStackTrace(result.failed.get)}")
      result
    }
  }

}



