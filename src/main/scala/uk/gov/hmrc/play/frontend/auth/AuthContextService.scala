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

import play.api.Logger
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse}
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Authority
import uk.gov.hmrc.play.frontend.auth.connectors.{AuthConnector, DelegationConnector}

import scala.concurrent.{ExecutionContext, Future}

private[auth] trait AuthContextService {

  self: DelegationDataProvider =>

  protected def authConnector: AuthConnector

  private[auth] def currentAuthContext(sessionData: UserSessionData)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AuthContext]] = {

    sessionData.userId match {
      // during the transition period this URI may point to either a session record or an old auth record
      case Some(authorityUri) => loadAuthContext(authorityUri, sessionData.governmentGatewayToken, sessionData.name, sessionData.delegationState)
      case None => Future.successful(None)
    }
  }

  private def loadAuthContext(authorityUri: String,
                              governmentGatewayToken: Option[String],
                              nameFromSession: Option[String],
                              delegationState: DelegationState)
                             (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AuthContext]] = {

    val authorityResponse = loadAuthority(authorityUri)

    def loadDelegationData(auth: Authority): Future[Option[DelegationData]] = delegationState match {
      case DelegationOn => self.loadDelegationData(auth.legacyOid)
      case _ => Future.successful(None)
    }

    authorityResponse.flatMap {
      case Some(authority) => loadDelegationData(authority).map { delegationData =>
        Some(AuthContext(authority, governmentGatewayToken, nameFromSession, delegationData))
      }
      case None => Future.successful(None)
    }
  }

  private def loadAuthority(authorityUri: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Authority]] = {

    authConnector.currentAuthority.map {
      case Some(authority) if authority.uri == authorityUri => Some(authority)
      case Some(authority) if authority.uri != authorityUri =>
        Logger.warn(s"Current Authority uri does not match session URI '$authorityUri', ending session.  Authority found was: $authority")
        None
      case None => None
    } recover {
      case Upstream4xxResponse(_, 401, _, _) => None
    }
  }
}

private[auth] sealed trait DelegationDataProvider {
  protected def loadDelegationData(oid: String)(implicit hc: HeaderCarrier): Future[Option[DelegationData]]
}

private[auth] trait DelegationDisabled extends DelegationDataProvider {
  protected override def loadDelegationData(oid: String)(implicit hc: HeaderCarrier): Future[Option[DelegationData]] =
    Future.successful(None)
}

private[auth] trait DelegationEnabled extends DelegationDataProvider {

  protected def delegationConnector: DelegationConnector

  protected override def loadDelegationData(oid: String)(implicit hc: HeaderCarrier): Future[Option[DelegationData]] = {
    delegationConnector.getDelegationData(oid)
  }
}
