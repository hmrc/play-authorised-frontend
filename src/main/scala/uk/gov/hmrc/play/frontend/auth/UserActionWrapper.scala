/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent._

trait UserActionWrapper extends Results {

  self: AuthContextService =>

  private[auth] def WithUserAuthenticatedBy(authenticationProvider: AuthenticationProvider,
                                            taxRegime: Option[TaxRegime])
                                           (userAction: AuthContext => Action[AnyContent]): Action[AnyContent] =
    Action.async {
      implicit request =>
        implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers,Some(request.session) )
        Logger.info(s"WithUserAuthorisedBy using auth provider ${authenticationProvider.id}")
        val handle =
          authenticationProvider.handleNotAuthenticated orElse
            authenticationProvider.handleAuthenticated orElse
            handleAuthenticated(taxRegime, authenticationProvider)

        handle(UserSessionData(request.session).userCredentials).flatMap {
          case Left(successfullyFoundUser) => userAction(successfullyFoundUser)(request)
          case Right(resultOfFailure) => Action(resultOfFailure)(request)
        }
    }

  private def handleAuthenticated(taxRegime: Option[TaxRegime], authenticationProvider: AuthenticationProvider)
                                 (implicit request: Request[AnyContent]):
  PartialFunction[UserCredentials, Future[Either[AuthContext, Result]]] = {
    case UserCredentials(Some(userId), tokenOption) =>
      implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers,Some(request.session) )

      currentAuthContext(UserSessionData(request.session)).flatMap {

        case Some(authContext) => taxRegime match {
          case Some(regime) if !regime.isAuthorised(authContext.principal.accounts) =>
            Logger.info("user not authorised for " + regime.getClass)
            regime.unauthorisedLandingPage match {
              case Some(redirectUrl) => Future.successful(Right(Redirect(redirectUrl)))
              case None => authenticationProvider.redirectToLogin.map(Right(_))
            }
          case _ => Future.successful(Left(authContext))
        }
        case None =>
          Logger.warn(s"No authority found for user id '$userId' from '${request.remoteAddress}'")
          authenticationProvider.redirectToLogin.map {
            result =>
              Right(result.withNewSession)
          }
      }
  }
}
