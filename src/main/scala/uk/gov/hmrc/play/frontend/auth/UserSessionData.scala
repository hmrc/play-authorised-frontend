/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.mvc.Session
import uk.gov.hmrc.http.SessionKeys

case class UserSessionData(userId: Option[String], governmentGatewayToken: Option[String], name: Option[String], delegationState: DelegationState) {
  val userCredentials = UserCredentials(userId, governmentGatewayToken)
}

object UserSessionData {

  private[auth] val DelegationStateSessionKey = "delegationState"

  def apply(session: Session): UserSessionData = UserSessionData(
    userId = session.get(SessionKeys.userId),
    governmentGatewayToken = session.get(SessionKeys.token),
    name = session.get(SessionKeys.name),
    delegationState = DelegationState(session.get(DelegationStateSessionKey))
  )
}
