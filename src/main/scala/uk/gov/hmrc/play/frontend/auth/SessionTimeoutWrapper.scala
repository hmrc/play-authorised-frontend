/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.play.frontend.auth

import org.joda.time.DateTime
import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.time.DateTimeUtils
import scala.concurrent._

trait SessionTimeoutWrapper {

  def now: () => DateTime = () => DateTimeUtils.now

  object WithSessionTimeoutValidation extends WithSessionTimeoutValidation(now)

  object WithNewSessionTimeout extends WithNewSessionTimeout(now)

}

class WithSessionTimeoutValidation(val now: () => DateTime) extends SessionTimeout {

  def apply(authenticationProvider: AuthenticationProvider)(action: Action[AnyContent]): Action[AnyContent] = Action.async {
    implicit request: Request[AnyContent] => {
      implicit val loggingDetails = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

      def preserveSession(timeoutRequest: Result): Seq[(String, String)] = for {
        key <- authenticationProvider.sessionKeysToKeep
        value <- timeoutRequest.session.get(key)
      } yield key -> value

      val result = if (authenticationProvider.userNeedsNewSession(request.session, now)) {
        Logger.info(s"request refused as the session had timed out in ${request.path}")
        for {
          result <- authenticationProvider.handleSessionTimeout
          timeoutRequest <- Action(result)(request)
          consumeSessionKeys = preserveSession(timeoutRequest)
      } yield timeoutRequest.withSession(consumeSessionKeys: _*)
    } else
      action(request)
    addTimestamp(request, result)
  }
}

}

class WithNewSessionTimeout(val now: () => DateTime) extends SessionTimeout {
  def apply(action: Action[AnyContent]) = Action.async {
    request: Request[AnyContent] => {
      addTimestamp(request, action(request))
    }
  }
}

trait SessionTimeout {

  import org.joda.time.DateTime
  import play.api.http.HeaderNames.SET_COOKIE

  val now: () => DateTime

  protected def addTimestamp(request: Request[AnyContent], result: Future[Result]): Future[Result] = {
    implicit val headerCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
    result.map(insertTimestampNow(request))
  }

  private def insertTimestampNow(request: Request[AnyContent])(result: Result): Result = {
    val sessionData = session(result).getOrElse(request.session).data.toSeq
    val newSessionData = sessionData :+ (SessionKeys.lastRequestTimestamp -> now().getMillis.toString)
    result.withSession(newSessionData: _*)
  }

  private def session(result: Result): Option[Session] = for {
    resultSetCookieHeader <- result.header.headers.get(SET_COOKIE)
    resultSessionCookie <- Cookies.fromSetCookieHeader(Some(resultSetCookieHeader)).get(Session.COOKIE_NAME)
  } yield Session.decodeFromCookie(Some(resultSessionCookie))
}
