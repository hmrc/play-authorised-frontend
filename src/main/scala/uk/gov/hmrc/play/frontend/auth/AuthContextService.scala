/*
 * Copyright 2016 HM Revenue & Customs
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
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Authority
import uk.gov.hmrc.play.frontend.auth.connectors.{AuthConnector, DelegationConnector}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.http.Upstream4xxResponse

import scala.concurrent.Future

private[auth] trait AuthContextService {

  self: DelegationDataProvider =>

  protected def authConnector: AuthConnector

  private[auth] def currentAuthContext(sessionData: UserSessionData)(implicit hc: HeaderCarrier): Future[Option[AuthContext]] = {

    sessionData.userId match {
      case Some(userId) => loadAuthContext(userId, sessionData.governmentGatewayToken, sessionData.name, sessionData.delegationState)
      case None => Future.successful(None)
    }
  }

  private def loadAuthContext(userId: String,
                              governmentGatewayToken: Option[String],
                              nameFromSession: Option[String],
                              delegationState: DelegationState)
                             (implicit hc: HeaderCarrier): Future[Option[AuthContext]] = {

    val authorityResponse = loadAuthority(userId)

    val delegationDataResponse = delegationState match {
      case DelegationOn => loadDelegationData(userId)
      case _ => Future.successful(None)
    }

    authorityResponse.flatMap {
      case Some(authority) => delegationDataResponse.map { delegationData =>
        Some(AuthContext(authority, governmentGatewayToken, nameFromSession, delegationData))
      }
      case None => Future.successful(None)
    }
  }

  private def loadAuthority(userId: String)(implicit hc: HeaderCarrier): Future[Option[Authority]] = {

    authConnector.currentAuthority.map {
      case Some(authority) if authority.uri == userId => Some(authority)
      case Some(authority) if authority.uri != userId =>
        Logger.warn(s"Current Authority uri does not match session userId '$userId', ending session.  Authority found was: $authority")
        None
      case None => None
    } recover {
      case Upstream4xxResponse(_, 401, _, _) => None
    }
  }
}

private[auth] sealed trait DelegationDataProvider {
  protected def loadDelegationData(userId: String)(implicit hc: HeaderCarrier): Future[Option[DelegationData]]
}

private[auth] trait DelegationDisabled extends DelegationDataProvider {
  protected override def loadDelegationData(userId: String)(implicit hc: HeaderCarrier): Future[Option[DelegationData]] =
    Future.successful(None)
}

private[auth] trait DelegationEnabled extends DelegationDataProvider {

  protected def delegationConnector: DelegationConnector

  protected override def loadDelegationData(userId: String)(implicit hc: HeaderCarrier): Future[Option[DelegationData]] = {
    delegationConnector.getDelegationData(OidExtractor.userIdToOid(userId))
  }
}
