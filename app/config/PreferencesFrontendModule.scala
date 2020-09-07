/*
 * Copyright 2020 HM Revenue & Customs
 *
 */

package config

import com.google.inject.AbstractModule
import javax.inject.{ Inject, Singleton }
import play.api.http.HttpConfiguration
import play.api.i18n.{ DefaultMessagesApiProvider, Langs }
import play.api.{ Configuration, Environment }

class PreferencesFrontendModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[DefaultMessagesApiProvider]).to(classOf[CohortMessagesApiProvider])

}

@Singleton
class CohortMessagesApiProvider @Inject()(
  environment: Environment,
  config: Configuration,
  langs: Langs,
  httpConfiguration: HttpConfiguration)
    extends DefaultMessagesApiProvider(environment, config, langs, httpConfiguration) {

  override protected def loadAllMessages: Map[String, Map[String, String]] =
    super.loadAllMessages
      .map { case (k, v) => if (k == "default") (k, merge(v, loadMessages("ipage.messages"))) else (k, v) }
      .map { case (k, v) => if (k == "cy") (k, merge(v, loadMessages("ipage.messages.cy"))) else (k, v) }
      .map { case (k, v) => if (k == "default") (k, merge(v, loadMessages("tcpage.messages"))) else (k, v) }
      .map { case (k, v) => if (k == "cy") (k, merge(v, loadMessages("tcpage.messages.cy"))) else (k, v) }
      .map { case (k, v) => if (k == "default") (k, merge(v, loadMessages("reoptinpage.messages"))) else (k, v) }
      .map { case (k, v) => if (k == "cy") (k, merge(v, loadMessages("reoptinpage.messages.cy"))) else (k, v) }

  private def merge(m1: Map[String, String], m2: Map[String, String]): Map[String, String] =
    (m1.toSeq ++ m2.toSeq).groupBy(_._1).mapValues(_(0)._2)
}