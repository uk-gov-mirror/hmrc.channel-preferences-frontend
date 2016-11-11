package partial.paperless.manage

import connectors.SaEmailPreference.Status
import connectors.SaPreference
import model.HostContext
import play.api.mvc.Request
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import play.twirl.api.HtmlFormat

object ManagePaperlessPartial {
  def apply(prefs: Option[SaPreference])(implicit request: Request[_], hostContext: HostContext): HtmlFormat.Appendable = prefs match {
    case Some(SaPreference(true, Some(email))) => email.status match {
      case Status.Pending  => html.digital_true_pending(email)
      case Status.Verified => html.digital_true_verified(email)
      case Status.Bounced  => html.digital_true_bounced(email)
    }
    case _ => html.digital_false()
  }
}
