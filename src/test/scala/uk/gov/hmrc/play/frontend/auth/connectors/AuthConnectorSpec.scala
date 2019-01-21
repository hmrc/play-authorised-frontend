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

package uk.gov.hmrc.play.frontend.auth.connectors

import org.mockito.Matchers.{eq => meq}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.frontend.auth._
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, ConfidenceLevel, CredentialStrength}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class AuthConnectorSpec extends UnitSpec with WithFakeApplication with WireMockedSpec with ScalaFutures {

  private implicit val hc = HeaderCarrier()

  case class Ids(internalId: String, externalId: String)

  object Ids {
    implicit val format = Json.format[Ids]
  }
  
  case class MinimalUserDetails(name: String, email: String)
  
  object MinimalUserDetails {
    implicit val format = Json.format[MinimalUserDetails]
  }
  
  case class EnrolmentIdentifier(key: String, value: String)

  case class Enrolment(key: String, identifiers: Seq[EnrolmentIdentifier], state: String)
  
  object Enrolment {
    implicit val idformat = Json.format[EnrolmentIdentifier]
    implicit val format = Json.format[Enrolment]
  }

  trait TestHttp extends CoreGet with CorePut with CoreDelete
  trait Setup extends MockitoSugar {
    
    val loggedInUser = LoggedInUser("/auth/123", None, None, None, CredentialStrength.Strong, ConfidenceLevel.L500, "123")
    val principal = Principal(Some("Bob P"), Accounts())

    val authContext = AuthContext(
      loggedInUser, 
      principal, 
      attorney = None, 
      userDetailsUri = Some("/user-details/12345"), 
      enrolmentsUri = Some("/auth/oid/12345/enrolments"),
      idsUri = Some("/auth/oid/12345/ids")
    )

    val mockHttp = mock[TestHttp]
    val connector = new AuthConnector {
      override val serviceUrl = s"http://localhost:$Port"
      override lazy val http = mockHttp
    }

  }

  "The getIds method" should {
    "fail with NotFoundException if the affordance is empty" in new Setup {
      val authContextWithoutURI = authContext.copy(idsUri = None)
      connector.getIds[Ids](authContextWithoutURI).failed.futureValue shouldBe a[NotFoundException]
    }
  }
  
  "The getEnrolments method" should {
    "fail with NotFoundException if the affordance is empty" in new Setup {
      val authContextWithoutURI: AuthContext = authContext.copy(enrolmentsUri = None)
      connector.getEnrolments[Set[Enrolment]](authContextWithoutURI).failed.futureValue shouldBe a[NotFoundException]
    }
  }
  
  "The getUserDetails method" should {
    "fail with NotFoundException if the affordance is empty" in new Setup {
      val authContextWithoutURI: AuthContext = authContext.copy(userDetailsUri = None)
      connector.getUserDetails[MinimalUserDetails](authContextWithoutURI).failed.futureValue shouldBe a[NotFoundException]
    }
  }

}
