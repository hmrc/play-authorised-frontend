/*
 * Copyright 2019 HM Revenue & Customs
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

import org.joda.time.{DateTime, DateTimeZone, Duration}
import play.api.Play.current
import play.api.mvc.Results._
import play.api.mvc._
import play.api.{Logger, Play}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent._

case class UserCredentials(userId: Option[String], token: Option[String])

object AuthenticationProviderIds {
  val GovernmentGatewayId = "GGW"
  val AnyAuthenticationProviderId = "IDAorGGW"
  val VerifyProviderId = "IDA"
}

trait AuthenticationProvider {
  def defaultTimeoutSeconds: Int = 900

  type FailureResult = Result

  def id: String

  def sessionKeysToKeep : Seq[String] = Seq.empty

  def redirectToLogin(implicit request: Request[_]): Future[Result]

  def handleSessionTimeout(implicit request: Request[_]): Future[Result] = redirectToLogin

  def handleNotAuthenticated(implicit request: Request[_]): PartialFunction[UserCredentials, Future[Either[AuthContext, FailureResult]]]

  def handleAuthenticated(implicit request: Request[_]): PartialFunction[UserCredentials, Future[Either[AuthContext, Result]]] = PartialFunction.empty

  implicit def hc(implicit request: Request[_]) = HeaderCarrierConverter.fromHeadersAndSession(request.headers,Some(request.session) )

  final def userNeedsNewSession(session: Session, now: () => DateTime): Boolean = {
    extractTimestamp(session).fold(false)(hasExpired(now))
  }

  private def extractTimestamp(session: Session): Option[DateTime] = {
    try {
      session.get(SessionKeys.lastRequestTimestamp) map (t => new DateTime(t.toLong, DateTimeZone.UTC))
    } catch {
      case e: NumberFormatException => None
    }
  }

  private def timeoutSeconds : Int = {
    if (defaultTimeoutSeconds < 300 || defaultTimeoutSeconds > 1800) throw new IllegalArgumentException("session timeout must be between 300 and 1800 seconds")
    defaultTimeoutSeconds
  }

  private def hasExpired(now: () => DateTime)(timestamp: DateTime): Boolean = {
    val timeOfExpiry = timestamp plus Duration.standardSeconds(timeoutSeconds)
    now() isAfter timeOfExpiry
  }
}

trait GovernmentGateway extends AuthenticationProvider {

  override val id = AuthenticationProviderIds.GovernmentGatewayId

  final lazy val defaultOrigin: String = {
    Play.configuration.getString("sosOrigin")
      .orElse(Play.configuration.getString("appName"))
      .getOrElse("undefined")
  }
  
  def origin: String = defaultOrigin
  
  def continueURL: String
  
  def loginURL: String

  def additionalLoginParameters: Map[String, Seq[String]] = Map.empty

  private def loginUrlParameters = Map("continue" -> Seq(continueURL), "origin" -> Seq(origin)) ++ additionalLoginParameters

  def redirectToLogin(implicit request: Request[_]) = Future.successful(Redirect(loginURL, loginUrlParameters))


  def handleNotAuthenticated(implicit request: Request[_]): PartialFunction[UserCredentials, Future[Either[AuthContext, FailureResult]]] = {
    case UserCredentials(None, token@_) =>
      Logger.info(s"No userId found - redirecting to login. user: None token : $token")
      redirectToLogin.map(Right(_))
    case UserCredentials(Some(userId), None) =>
      Logger.info(s"No gateway token - redirecting to login. user : $userId token : None")
      redirectToLogin.map(Right(_))
  }
}

trait Verify extends AuthenticationProvider {

  override val id = AuthenticationProviderIds.VerifyProviderId

  override def sessionKeysToKeep : Seq[String] = Seq(SessionKeys.loginOrigin, SessionKeys.redirect)

  def login: String

  def redirectToLogin(implicit request: Request[_]) = Future.successful(Redirect(login))

  def handleNotAuthenticated(implicit request: Request[_]): PartialFunction[UserCredentials, Future[Either[AuthContext, FailureResult]]] = {
    case UserCredentials(None, None) =>
      Logger.info(s"No userId found - unauthorized. user: None")
      redirectToLogin.map(Right(_))
  }
}

trait AnyAuthenticationProvider extends AuthenticationProvider {

  val id = AuthenticationProviderIds.AnyAuthenticationProviderId

  override def sessionKeysToKeep : Seq[String] = Seq(SessionKeys.loginOrigin, SessionKeys.redirect)

  def ggwAuthenticationProvider: GovernmentGateway
  def verifyAuthenticationProvider: Verify

  def login: String

  def redirectToLogin(implicit request: Request[_]) = Future.successful(Redirect(login))

  private def handleMissingProvider(implicit request: Request[_]): PartialFunction[UserCredentials, Future[Either[AuthContext, FailureResult]]] = {
    case _ =>
      Logger.info("No provider in the session")
      redirectToLogin.map(Right(_))
  }

  def handleNotAuthenticated(implicit request: Request[_]) =
    request.session.get(SessionKeys.authProvider) match {
      case Some(AuthenticationProviderIds.GovernmentGatewayId) =>
        ggwAuthenticationProvider.handleNotAuthenticated
      case Some(AuthenticationProviderIds.VerifyProviderId) =>
        verifyAuthenticationProvider.handleNotAuthenticated
      case _ =>
        handleMissingProvider
    }
}
