package controllers.common.actions

import uk.gov.hmrc.utils.DateConverter
import config.DateTimeProvider
import util.Success


trait LoggingActionWrapper {

  import controllers.common.HeaderNames

  object WithRequestLogging extends HeaderNames with MdcHelper with DateConverter {

    import play.api.mvc._
    import java.text.SimpleDateFormat
    import java.util.Date
    import play.api.Logger
    import scala.concurrent.ExecutionContext

    import ExecutionContext.Implicits.global

    private val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZZZ")

    def apply(action: Action[AnyContent]): Action[AnyContent] = Action.async {
      request =>
        {
          val start = DateTimeProvider.now().getMillis
          val startTime = format.format(new Date(start))
          val mdc = fromMDC()

          action(request).andThen { case Success(result) =>
            val elapsedTime = DateTimeProvider.now().getMillis - start

            // Apache combined log format http://httpd.apache.org/docs/2.4/logs.html
            Logger.info(s"${mdc.get(forwardedFor).getOrElse(request.remoteAddress)} ${mdc.get(requestId).getOrElse("-")} " +
              s"${mdc.get(authorisation).getOrElse("-")} [$startTime] '${request.method} ${request.uri}' ${result.header.status} " +
              s"- ${elapsedTime}ms '${request.headers.get("Referer").getOrElse("-")}' '${request.headers.get("User-Agent").getOrElse("-")}'")
          }
        }
    }
  }
}
