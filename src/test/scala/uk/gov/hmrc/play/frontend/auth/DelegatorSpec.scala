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

import org.scalatest.mock.MockitoSugar
import play.api.mvc.{RequestHeader, Results}
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.{AgentCode, AgentUserId, Nino, SaUtr}
import uk.gov.hmrc.play.frontend.auth.connectors.DelegationConnector
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class DelegatorSpec extends UnitSpec with WithFakeApplication with Results {

  import org.mockito.Mockito._

  implicit val authContext = AuthContext(
    user = LoggedInUser("/auth/12345", None, None, None, CredentialStrength.Strong, ConfidenceLevel.L500, "12345"),
    principal = Principal(
      name = Some("Dave Agent"),
      accounts = Accounts(agent = Some(AgentAccount(
        link = "http//agent/4567",
        agentCode = AgentCode("4567"),
        agentUserId = AgentUserId("ABC"),
        agentUserRole = AgentAdmin,
        payeReference = None,
        agentBusinessUtr = None
      )))
    ),
    attorney = None,
    userDetailsUri = Some("/user-details/1234567890"),
    enrolmentsUri = Some("/auth/oid/1234567890/enrolments"),
    idsUri = Some("/auth/oid/1234567890/ids")
  )

  implicit val hc = new HeaderCarrier()

  "The startDelegation method" should {

    "pass the delegation data to the connector and return a redirect response with the delegation session flag" in new TestCase {

      implicit val request: RequestHeader = FakeRequest()

      val delegationContext = DelegationContext(
        principalName = "Dave Client",
        attorneyName = "Bob Agent",
        principalTaxIdentifiers = TaxIdentifiers(
          paye = Some(Nino("AB123456D")),
          sa = Some(SaUtr("1234567890"))
        ),
        link = Link(url = "http://taxplatform/some/dashboard", text = "Back to dashboard")
      )

      val redirectTo = "http://blah/blah"

      when(mockDelegationConnector.startDelegation("12345", delegationContext)).thenReturn(Future.successful(()))

      val result = await(delegator.startDelegationAndRedirect(delegationContext, redirectTo))

      result.header.status shouldBe 303
      result.header.headers.get("Location") shouldBe Some("http://blah/blah")

      UserSessionData(result.session).delegationState shouldBe DelegationOn

      verify(mockDelegationConnector).startDelegation("12345", delegationContext)
    }
  }

  "The endDelegation method" should {

    "call the delegation connector to end delegation and remove the delegation session flag" in new TestCase {

      implicit val request: RequestHeader = FakeRequest().withSession(UserSessionData.DelegationStateSessionKey -> DelegationOn.toString)

      assert(UserSessionData(request.session).delegationState == DelegationOn)

      when(mockDelegationConnector.endDelegation("12345")).thenReturn(Future.successful(()))

      val result = await(delegator.endDelegation(Ok))

      UserSessionData(result.session).delegationState shouldBe DelegationOff

      result.header.status shouldBe 200

      verify(mockDelegationConnector).endDelegation("12345")
    }
  }

  trait TestCase extends MockitoSugar {

    val mockDelegationConnector = mock[DelegationConnector]

    val delegator = new Delegator {
      override protected def delegationConnector: DelegationConnector = mockDelegationConnector
    }
  }
}
