package controllers.sa.prefs.service

import org.scalatest.{ ShouldMatchers, WordSpec }
import org.joda.time.{ DateTimeZone, DateTime }
import java.net.{ URLDecoder, URLEncoder }
import uk.gov.hmrc.crypto.AesCrypto
import uk.gov.hmrc.domain.SaUtr
import controllers.common.preferences.service.{TokenExpiredException, TokenEncryption}

class TokenEncryptionSpec extends WordSpec with ShouldMatchers {

  val crypto = new TokenEncryption with AesCrypto {
    override val encryptionKey = "P5xsJ9Nt+quxGZzB4DeLfw=="
  }

  "Token decryption" should {
    "decrypt a valid token" in {
      val validToken = s"utr:${DateTime.now(DateTimeZone.UTC).getMillis}"
      val encryptedToken = URLEncoder.encode(crypto.encrypt(validToken), "UTF-8")

      crypto.decryptToken(encryptedToken, 5) should have ('utr (SaUtr("utr")))
    }

    "decrypt a valid unencoded token" in {
      val validToken = s"cjsajjdajdas:${DateTime.now(DateTimeZone.UTC).getMillis}"
      val encryptedToken = crypto.encrypt(validToken)

      crypto.decryptToken(encryptedToken, 5) should have ('utr (SaUtr("cjsajjdajdas")))
    }

    "decrypt token with slashes and plus chars" in {
      val encoded = "vK%2Bps%2FoV3CYFc0fgzd1ZiBIUu%2FQ%2FVmAeDNcUkgRs%2BTE%3D"
      val token = URLDecoder.decode(encoded, "UTF-8")
      crypto.decrypt(token) shouldBe "cjsajjdajdas:1379068252455"
    }

    "fail with expired token" in {
      val expiredToken = s"utr:${DateTime.now(DateTimeZone.UTC).minusMinutes(6).getMillis}"
      val encryptedToken = URLEncoder.encode(crypto.encrypt(expiredToken), "UTF-8")

      intercept[TokenExpiredException] {
        crypto.decryptToken(encryptedToken, 5)
      }
    }

    "fail with corrupted token" in {
      intercept[SecurityException] {
        crypto.decryptToken("invalid", 5)
      }
    }
  }

}

