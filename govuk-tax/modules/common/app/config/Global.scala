package config


import GlobalHelper._
import com.kenshoo.play.metrics.MetricsFilter
import com.codahale.metrics.graphite.{GraphiteReporter, Graphite}
import java.net.InetSocketAddress
import com.codahale.metrics.{MetricFilter, SharedMetricRegistries}
import java.util.concurrent.TimeUnit
import play.api._
import play.api.mvc._
import play.filters.csrf._
import scala.concurrent.Future
import play.api.mvc.Results._
import scala.Some
import play.api.i18n.Messages
import uk.gov.hmrc.common.filters.{CacheControlFilter, SessionCookieCryptoFilter, CSRFExceptionsFilter}
import uk.gov.hmrc.common.crypto.ApplicationCrypto
import controllers.common.actions.HeaderCarrier

object Global
  extends WithFilters(MetricsFilter,
    SessionCookieCryptoFilter,
    CSRFExceptionsFilter,
    CSRFFilter(),
    CacheControlFilter.fromConfig("caching.allowedContentTypes")) {

  import controllers.common.service.Connectors._

  override def onStart(app: Application) {
    val env = app.mode
    if (app.configuration.getBoolean("metrics.enabled").getOrElse(false) &&
      app.configuration.getBoolean(s"govuk-tax.$env.metrics.graphite.enabled").getOrElse(false)) {
      startGraphite(app)
    }

    ApplicationCrypto.verifyConfiguration()
  }

  def startGraphite(app: Application) {
    val env = app.mode

    val graphite = new Graphite(new InetSocketAddress(
      app.configuration.getString(s"govuk-tax.$env.metrics.graphite.host").getOrElse("graphite"),
      app.configuration.getInt(s"govuk-tax.$env.metrics.graphite.port").getOrElse(2003)))

    val prefix = app.configuration.getString(s"govuk-tax.$env.metrics.graphite.prefix").getOrElse("tax")

    val reporter = GraphiteReporter.forRegistry(
      SharedMetricRegistries.getOrCreate(app.configuration.getString("metrics.name").getOrElse("default")))
      .prefixedWith(s"$prefix.${java.net.InetAddress.getLocalHost.getHostName}")
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .filter(MetricFilter.ALL)
      .build(graphite)

    reporter.start(app.configuration.getLong(s"govuk-tax.$env.metrics.graphite.interval").getOrElse(10L), TimeUnit.SECONDS)
  }

  // Play 2.0 doesn't support trailing slash: http://play.lighthouseapp.com/projects/82401/tickets/98
  override def onRouteRequest(request: RequestHeader) = super.onRouteRequest(request).orElse {
    Some(request.path).filter(_.endsWith("/")).flatMap(p => super.onRouteRequest(request.copy(path = p.dropRight(1))))
  }

  override def onError(request: RequestHeader, ex: Throwable): Future[SimpleResult] = {
    implicit val hc = HeaderCarrier(request)
    Future.successful {
      val (event, simpleResult) = resolveError(request, ex)
      auditConnector.audit(event)
      simpleResult
    }
  }

  override def onHandlerNotFound(request: RequestHeader) = {
    implicit val hc = HeaderCarrier(request)
    Future.successful {
      auditConnector.audit(buildAuditEvent("ServerValidationError", "Resource Endpoint Not Found", request, hc))
      NotFound(views.html.global_error(Messages("global.error.pageNotFound404.title"),
        Messages("global.error.pageNotFound404.heading"),
        Messages("global.error.pageNotFound404.message"))
      )
    }
  }


  override def onBadRequest(request: RequestHeader, error: String) = {
    implicit val hc = HeaderCarrier(request)
    Future.successful {
      auditConnector.audit(buildAuditEvent("ServerValidationError", "Request bad format exception", request, hc))
      BadRequest(
        views.html.global_error(Messages("global.error.badRequest400.title"),
          Messages("global.error.badRequest400.heading"),
          Messages("global.error.badRequest400.message"))
      )
    }
  }

}
