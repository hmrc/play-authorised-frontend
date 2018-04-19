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

import play.api.mvc._
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel

import scala.concurrent.Future

trait Actions extends UserActions with DelegationDisabled
trait DelegationAwareActions extends UserActions with DelegationEnabled

sealed trait UserActions
  extends SessionTimeoutWrapper
  with UserActionWrapper
  with AuthContextService
  with Authoriser
  with Results {

  self: DelegationDataProvider =>

  private type PlayRequest = (Request[AnyContent] => Result)
  private type AsyncPlayRequest = (Request[AnyContent] => Future[Result])
  private type PlayUserRequest = AuthContext => PlayRequest
  private type AsyncPlayUserRequest = AuthContext => AsyncPlayRequest

  type UserAction = AuthContext => Action[AnyContent]

  val VerifyConfidence = new NonNegotiableIdentityConfidencePredicate(ConfidenceLevel.L500)
  val GGConfidence = new NonNegotiableIdentityConfidencePredicate(ConfidenceLevel.L50)

  implicit def makeAction(body: PlayUserRequest): UserAction = (authContext: AuthContext) => Action(body(authContext))

  implicit def makeFutureAction(body: AsyncPlayUserRequest): UserAction = (authAction: AuthContext) => Action.async(body(authAction))

  def AuthorisedFor(taxRegime: TaxRegime,
                    pageVisibility: PageVisibilityPredicate) =
    new AuthenticatedBy(taxRegime.authenticationType, Some(taxRegime), pageVisibility)

  def AuthenticatedBy(authenticationProvider: AuthenticationProvider,
                      pageVisibility: PageVisibilityPredicate) =
    new AuthenticatedBy(authenticationProvider, None, pageVisibility)


  class AuthenticatedBy(authenticationProvider: AuthenticationProvider,
                        taxRegime: Option[TaxRegime],
                        pageVisibility: PageVisibilityPredicate) extends AuthenticatedAction {
    def apply(body: PlayUserRequest): Action[AnyContent] =
      authorised(authenticationProvider, taxRegime, pageVisibility, body)

    def async(body: AsyncPlayUserRequest): Action[AnyContent] =
      authorised(authenticationProvider, taxRegime, pageVisibility, body)

    private def authorised(authenticationProvider: AuthenticationProvider,
                           taxRegime: Option[TaxRegime],
                           pageVisibility: PageVisibilityPredicate,
                           body: UserAction) =
      WithSessionTimeoutValidation(authenticationProvider) {
        WithUserAuthenticatedBy(authenticationProvider, taxRegime) {
          authContext =>
            WithPageVisibility(pageVisibility, authContext) {
              implicit authContext => body(authContext)
            }
        }
      }
  }

}
