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

package uk.gov.hmrc.play.frontend.auth.connectors

import org.mockito.Matchers.{any, eq => meq}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.frontend.auth._
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, PayeAccount, SaAccount}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DelegationConnectorSpec extends UnitSpec with WithFakeApplication  {

  private implicit val hc = HeaderCarrier()

  "The getDelegationData response handler" should {

    val delegationDataObject = DelegationData(
      principalName = "Dave Client",
      attorneyName = "Bob Agent",
      accounts = Accounts(
        paye = Some(PayeAccount(link = "http://paye/some/path", nino = Nino("AB123456D"))),
        sa = Some(SaAccount(link = "http://sa/some/utr", utr = SaUtr("1234567890")))
      ),
      link = Link(url = "http://taxplatform/some/dashboard", text = "Back to dashboard")
    )

    val delegationDataJson = Json.obj(
      "attorneyName" -> "Bob Agent",
      "principalName" -> "Dave Client",
      "link" -> Json.obj(
        "url" -> "http://taxplatform/some/dashboard",
        "text" -> "Back to dashboard"
      ),
      "accounts" -> Json.obj(
        "paye" -> Json.obj(
          "link" -> "http://paye/some/path",
          "nino" -> "AB123456D"
        ),
        "sa" -> Json.obj(
          "link" -> "http://sa/some/utr",
          "utr" -> "1234567890"
        )
      )
    )

    "return the delegation data returned from the service, if the response code is 200" in new TestCase {
      val response = HttpResponse(200, Some(delegationDataJson))
      connector.responseHandler.read("GET", s"/oid/$oid", response) shouldBe Some(delegationDataObject)
    }

    "return None when the response code is 404" in new TestCase {
      val response = HttpResponse(404)
      connector.responseHandler.read("GET", s"/oid/$oid", response) shouldBe None
    }

    "throw an exception if the response code is anything other than 200 or 404" in new TestCase {
      val oid204 = "204oid"
      val oid400 = "400oid"
      val oid500 = "500oid"

      a[DelegationServiceException] should be thrownBy connector.responseHandler.read("GET", s"/oid/$oid204", HttpResponse(204))
      a[DelegationServiceException] should be thrownBy connector.responseHandler.read("GET", s"/oid/$oid400", HttpResponse(400))
      a[DelegationServiceException] should be thrownBy connector.responseHandler.read("GET", s"/oid/$oid500", HttpResponse(500))
    }

    "throw an exception if the response is not valid JSON" in new TestCase {
      val response = HttpResponse(200, None, Map.empty, Some("{ not _ json :"))
      a[DelegationServiceException] should be thrownBy connector.responseHandler.read("GET", s"/oid/$oid", response)
    }

    "throw an exception if the response is valid JSON, but not representing Delegation Data" in new TestCase {
      val response = HttpResponse(200, None, Map.empty, Some("""{"valid":"json"}"""))
      a[DelegationServiceException] should be thrownBy connector.responseHandler.read("GET", s"/oid/$oid", response)
    }
  }

  "The startDelegation method" should {

    val delegationContextObject = DelegationContext(
      principalName = "Dave Client",
      attorneyName = "Bob Agent",
      principalTaxIdentifiers = TaxIdentifiers(
        paye = Some(Nino("AB123456D")),
        sa = Some(SaUtr("1234567890"))
      ),
      link = Link(url = "http://taxplatform/some/dashboard", text = "Back to dashboard")
    )

    val delegationContextJson = Json.obj(
      "attorneyName" -> "Bob Agent",
      "principalName" -> "Dave Client",
      "link" -> Json.obj(
        "url" -> "http://taxplatform/some/dashboard",
        "text" -> "Back to dashboard"
      ),
      "principalTaxIdentifiers" -> Json.obj(
        "paye" -> "AB123456D",
        "sa" -> "1234567890"
      )
    ).toString()

    "send the delegation data to the DelegationService, and succeed if the response code is 201" in new TestCase {
      when(mockHttp.PUT[DelegationContext, HttpResponse](meq(s"$baseUrl/oid/$oid"), meq(delegationContextObject))(any(), any(), any(), any()))
        .thenReturn(Future(HttpResponse(201)))

      await(connector.startDelegation(oid, delegationContextObject))
    }

    "send the delegation data to the DelegationService, and fail if the response code is anything other than 201" in new TestCase {
      val oid200 = "200oid"
      val oid204 = "204oid"

      when(mockHttp.PUT[DelegationContext, HttpResponse](meq(s"$baseUrl/oid/$oid200"), meq(delegationContextObject))(any(), any(), any(), any()))
        .thenReturn(Future(HttpResponse(200)))

      when(mockHttp.PUT[DelegationContext, HttpResponse](meq(s"$baseUrl/oid/$oid204"), meq(delegationContextObject))(any(), any(), any(), any()))
        .thenReturn(Future(HttpResponse(204)))

      a[DelegationServiceException] should be thrownBy await(connector.startDelegation(oid200, delegationContextObject))
      a[DelegationServiceException] should be thrownBy await(connector.startDelegation(oid204, delegationContextObject))
    }

    "send the delegation data to the DelegationService, and bubble up any exceptions thrown by http-verbs" in new TestCase {
      when(mockHttp.PUT[DelegationContext, HttpResponse](any(), any())
        (any(), any(), any(), any()))
        .thenThrow(new RuntimeException("Boom"))

      a[RuntimeException] should be thrownBy await(connector.startDelegation("url", delegationContextObject))
    }
  }

  "The endDelegation method" should {

    "request deletion from the Delegation Service and succeed if the result is 204" in new TestCase {
      when(mockHttp.DELETE[HttpResponse](meq(s"$baseUrl/oid/$oid"))(any(), any(), any())).thenReturn(Future(HttpResponse(204)))
      await(connector.endDelegation(oid))
    }

    "request deletion from the Delegation Service and succeed if the result is 404" in new TestCase {
      when(mockHttp.DELETE[HttpResponse](meq(s"$baseUrl/oid/$oid"))(any(), any(), any())).thenReturn(Future(HttpResponse(404)))
      await(connector.endDelegation(oid))
    }

    "request deletion from the Delegation Service and fail if the result anything other than 204 or 404" in new TestCase {
      val oid200 = "200oid"
      val oid201 = "201oid"
      val oid400 = "400oid"
      val oid500 = "500oid"

      when(mockHttp.DELETE[HttpResponse](meq(s"$baseUrl/oid/$oid200"))(any(), any(), any()))
        .thenReturn(Future(HttpResponse(200)))

      when(mockHttp.DELETE[HttpResponse](meq(s"$baseUrl/oid/$oid201"))(any(), any(), any()))
        .thenReturn(Future(HttpResponse(201)))

      when(mockHttp.DELETE[HttpResponse](meq(s"$baseUrl/oid/$oid400"))(any(), any(), any()))
        .thenReturn(Future(HttpResponse(400)))

      when(mockHttp.DELETE[HttpResponse](meq(s"$baseUrl/oid/$oid500"))(any(), any(), any()))
        .thenReturn(Future(HttpResponse(500)))

      a[DelegationServiceException] should be thrownBy await(connector.endDelegation(oid200))
      a[DelegationServiceException] should be thrownBy await(connector.endDelegation(oid201))
      a[DelegationServiceException] should be thrownBy await(connector.endDelegation(oid400))
      a[DelegationServiceException] should be thrownBy await(connector.endDelegation(oid500))
    }
  }

  trait TestHttp extends CoreGet with CorePut with CoreDelete
  trait TestCase extends MockitoSugar {

    val baseUrl = s"http://localhost"

    val mockHttp = mock[TestHttp]

    val connector = new DelegationConnector {
      override protected val serviceUrl = baseUrl
      override protected lazy val http = mockHttp
    }

    val oid = "1234"
  }
}
