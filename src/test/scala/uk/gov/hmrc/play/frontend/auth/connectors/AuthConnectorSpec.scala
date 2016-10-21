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

package uk.gov.hmrc.play.frontend.auth.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.frontend.auth._
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, PayeAccount, SaAccount}
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.hooks.HttpHook
import uk.gov.hmrc.play.http.ws._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.play.frontend.auth.connectors.domain.CredentialStrength
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import play.api.libs.json.JsString

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
  
  trait Setup {
    
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
    
    val connector = new AuthConnector {

      override val serviceUrl = s"http://localhost:$Port"

      override lazy val http = new WSHttp {
        override val hooks: Seq[HttpHook] = NoneRequired
      }
    }

  }
  
  
  "The getIds method" should {
    
    val idsBody = """{
      |  "internalId": "int-1234",
    	|  "externalId": "ext-1234"
      |}""".stripMargin

    def stubIds() = stubFor(get(urlEqualTo("/auth/oid/12345/ids")).willReturn(aResponse().withStatus(200).withBody(idsBody)))
    
    "return the ids returned from the service based on a relative URL, if the response code is 200" in new Setup {
      
      stubIds()
      
      await(connector.getIds[Ids](authContext)) shouldBe Ids("int-1234", "ext-1234")
    }
    
    "return the ids returned from the service based on an absolute URL, if the response code is 200" in new Setup {

      stubIds()
      
      val authContextWithAbsoluteURL = authContext.copy(idsUri = Some(s"http://localhost:$Port/auth/oid/12345/ids"))
      
      await(connector.getIds[Ids](authContextWithAbsoluteURL)) shouldBe Ids("int-1234", "ext-1234")
    }
    
    "return the ids returned from the service as raw JSON, if the response code is 200" in new Setup {
      
      stubIds()
      
      (await((connector.getIds(authContext)).json \ "internalId").get) shouldBe JsString("int-1234")
    }
    
    "fail with NotFoundException if the affordance is empty" in new Setup {

      stubIds()
      
      val authContextWithoutURI = authContext.copy(idsUri = None)
      
      connector.getIds[Ids](authContextWithoutURI).failed.futureValue shouldBe a[NotFoundException]
    }
    
  }
  
  
  "The getEnrolments method" should {
    
    val enrolmentsBody = """[
      |  {
      |    "key": "IR-SA",
      |    "identifiers": [
      |      {"key": "UTR", "value": "999902737"}
      |    ],
      |    "state": "Activated"
      |  },
      |  {
      |    "key": "IR-PAYE",
      |    "identifiers": [
      |      {"key": "TaxOfficeNumber", "value": "125"},
      |      {"key": "TaxOfficeReference", "value": "LZ00015"}
      |    ],
      |    "state": "Activated"
      |  }
      |]""".stripMargin
      
    val expectedResult = Set(
      Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", "999902737")), "Activated"),  
      Enrolment("IR-PAYE", Seq(EnrolmentIdentifier("TaxOfficeNumber", "125"), EnrolmentIdentifier("TaxOfficeReference", "LZ00015")), "Activated")  
    )

    def stubIds() = stubFor(get(urlEqualTo("/auth/oid/12345/enrolments")).willReturn(aResponse().withStatus(200).withBody(enrolmentsBody)))
    
    "return the enrolments returned from the service based on a relative URL, if the response code is 200" in new Setup {
      
      stubIds()
      
      await(connector.getEnrolments[Set[Enrolment]](authContext)) shouldBe expectedResult
    }
    
    "return the enrolments returned from the service based on an absolute URL, if the response code is 200" in new Setup {

      stubIds()
      
      val authContextWithAbsoluteURL = authContext.copy(enrolmentsUri = Some(s"http://localhost:$Port/auth/oid/12345/enrolments"))
      
      await(connector.getEnrolments[Set[Enrolment]](authContextWithAbsoluteURL)) shouldBe expectedResult
    }
    
    "return the enrolments returned from the service as raw JSON, if the response code is 200" in new Setup {
      
      stubIds()
      
      await(connector.getEnrolments(authContext)).json shouldBe Json.parse(enrolmentsBody)
    }
    
    "fail with NotFoundException if the affordance is empty" in new Setup {

      stubIds()
      
      val authContextWithoutURI = authContext.copy(enrolmentsUri = None)
      
      connector.getEnrolments[Set[Enrolment]](authContextWithoutURI).failed.futureValue shouldBe a[NotFoundException]
    }
    
  }
  
  
  "The getUserDetails method" should {
    
    val userDetailsBody = """{
      |  "name":"test",
      |  "email":"test@test.com",
      |  "affinityGroup" : "affinityGroup",
      |  "description" : "description",
      |  "lastName":"test",
      |  "dateOfBirth":"1980-06-30",
      |  "postcode":"NW94HD",
      |  "authProviderId": "12345-PID",
      |  "authProviderType": "Verify"
      |}""".stripMargin
      
    val expectedResult = MinimalUserDetails("test", "test@test.com")
      
    def stubIds() = stubFor(get(urlEqualTo("/user-details/12345")).willReturn(aResponse().withStatus(200).withBody(userDetailsBody)))
    
    "return the ids returned from the service based on a relative URL, if the response code is 200" in new Setup {
      
      stubIds()
      
      await(connector.getUserDetails[MinimalUserDetails](authContext)) shouldBe expectedResult
    }
    
    "return the ids returned from the service based on an absolute URL, if the response code is 200" in new Setup {

      stubIds()
      
      val authContextWithAbsoluteURL = authContext.copy(userDetailsUri = Some(s"http://localhost:$Port/user-details/12345"))
      
      await(connector.getUserDetails[MinimalUserDetails](authContextWithAbsoluteURL)) shouldBe expectedResult
    }
    
    "return the ids returned from the service as raw JSON, if the response code is 200" in new Setup {
      
      stubIds()
      
      await(connector.getUserDetails(authContext)).json shouldBe Json.parse(userDetailsBody)
    }
    
    "fail with NotFoundException if the affordance is empty" in new Setup {

      stubIds()
      
      val authContextWithoutURI = authContext.copy(userDetailsUri = None)
      
      connector.getUserDetails[MinimalUserDetails](authContextWithoutURI).failed.futureValue shouldBe a[NotFoundException]
    }
    
  }
  
  
}
