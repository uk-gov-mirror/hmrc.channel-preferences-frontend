/*
 * Copyright 2019 HM Revenue & Customs
 *
 */

import uk.gov.hmrc.domain.{ Nino, SaUtr }

import java.util.UUID
import scala.util.Random

object GenerateRandom {
  val rand = new Random()

  def email()() = s"${UUID.randomUUID()}@TEST.com"
  def utr()() = SaUtr(UUID.randomUUID.toString)
  def nino()() = Nino(f"CE${rand.nextInt(100000)}%06dD")
}
