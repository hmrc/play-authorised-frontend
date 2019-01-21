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

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future
import scala.util.Right

class AuthenticationProviderSpec extends UnitSpec with ScalaFutures with IntegrationPatience with WithFakeApplication {

  val ggLogin = "/gg"
  val verifyLogin = "/verify"
  val anyLogin = "/any"

  "handleNotAuthenticated" should {

    "return redirection to ggLogin for GGW authProvider and no GGW token in the session" in new AnyAuthProviderTestCase(authProviderInSession = Some("GGW")) {
      val userCredentials = UserCredentials(Some("validUser"), None)
      handleNotAuthenticated.isDefinedAt(userCredentials) should be (true)

      for {
        result <- handleNotAuthenticated(userCredentials).futureValue.right
      } yield {
        val location = redirectLocation(result)
        location shouldBe defined
        val fakeRequest = FakeRequest("GET", location.get)
        fakeRequest.path shouldBe ggLogin
        fakeRequest.getQueryString("origin") shouldBe Some("xyz")
        fakeRequest.getQueryString("continue") shouldBe Some("/calling-service?foo=bar&bar=foo")
      }
    }

    "return redirection to ggLogin for GGW authProvider and no user in the session" in new AnyAuthProviderTestCase(authProviderInSession = Some("GGW")) {
      val userCredentials = UserCredentials(None, None)
      handleNotAuthenticated.isDefinedAt(userCredentials) should be (true)

      for {
        result <- handleNotAuthenticated(userCredentials).futureValue.right
      } yield {
        val location = redirectLocation(result)
        location shouldBe defined
        val fakeRequest = FakeRequest("GET", location.get)
        fakeRequest.path shouldBe ggLogin
        fakeRequest.getQueryString("origin") shouldBe Some("xyz")
        fakeRequest.getQueryString("continue") shouldBe Some("/calling-service?foo=bar&bar=foo")
      }
    }

    "not handle authenticated user for GGW authProvider" in new AnyAuthProviderTestCase(authProviderInSession = Some("GGW")) {
      val userCredentials = UserCredentials(Some("validUser"), Some("validToken"))
      handleNotAuthenticated.isDefinedAt(userCredentials) should be (false)
    }

    "return redirection to verifyLogin for IDA authProvider and no user in the session" in new AnyAuthProviderTestCase(authProviderInSession = Some("IDA")) {
      val userCredentials = UserCredentials(None, None)
      handleNotAuthenticated.isDefinedAt(userCredentials) should be (true)

      for {
        result <- handleNotAuthenticated(userCredentials).futureValue.right
      } yield redirectLocation(result) should be (Some(verifyLogin))
    }

    "not handle authenticated user for IDA authProvider" in new AnyAuthProviderTestCase(authProviderInSession = Some("IDA")) {
      val userCredentials = UserCredentials(Some("validUser"), None)
      handleNotAuthenticated.isDefinedAt(userCredentials) should be (false)
    }

    "return redirection to anyLogin when no authProvider in the session for any credentials" in new AnyAuthProviderTestCase(authProviderInSession = None) {
      val userCredentials = UserCredentials(Some("validUser"), Some("validToken"))
      handleNotAuthenticated.isDefinedAt(userCredentials) should be (true)

      for {
        result <- handleNotAuthenticated(userCredentials).futureValue.right
      } yield redirectLocation(result) should be (Some(anyLogin))

    }

    "return redirection to anyLogin for unknown authProvider and for any credentials" in new AnyAuthProviderTestCase(authProviderInSession = Some("unknown")) {
      val userCredentials = UserCredentials(Some("validUser"), Some("validToken"))
      handleNotAuthenticated.isDefinedAt(userCredentials) should be (true)

      for {
        result <- handleNotAuthenticated(userCredentials).futureValue.right
      } yield redirectLocation(result) should be (Some(anyLogin))
    }

    "return Unauthorized for unknown authProvider and for any credentials" in new AnyAuthProviderTestCaseNoRedirect(authProviderInSession = Some("unknown")) {
      val userCredentials = UserCredentials(Some("validUser"), Some("validToken"))
      handleNotAuthenticated.isDefinedAt(userCredentials) should be (true)
      handleNotAuthenticated(userCredentials).futureValue should be (Right(Unauthorized))
    }

    "return Unauthorized to ggLogin for GGW authProvider and no GGW token in the session" in new AnyAuthProviderTestCaseNoRedirect(authProviderInSession = Some("GGW")) {
      val userCredentials = UserCredentials(Some("validUser"), None)
      handleNotAuthenticated.isDefinedAt(userCredentials) should be (true)
      handleNotAuthenticated(userCredentials).futureValue should be (Right(Unauthorized))
    }
  }

  class AnyAuthenticationProviderForTest extends AnyAuthenticationProvider {

    override def ggwAuthenticationProvider: GovernmentGateway = new GovernmentGateway {
      override def origin: String = "xyz"
      override def continueURL: String = "/calling-service?foo=bar&bar=foo"
      override def loginURL: String = ggLogin
    }
    override def verifyAuthenticationProvider: Verify = new Verify {
      override def login: String = verifyLogin
    }
    override def login: String = anyLogin
  }

  class AnyAuthenticationProviderForTestNoRedirect extends AnyAuthenticationProviderForTest {

    override def ggwAuthenticationProvider: GovernmentGateway = new GovernmentGateway {
      override def origin: String = "xyz"
      override def continueURL: String = "/calling-service"
      override def loginURL: String = ggLogin
      override def redirectToLogin(implicit request: Request[_]) = Future.successful(Unauthorized)
    }

    override def verifyAuthenticationProvider: Verify = new Verify {
      override def login: String = throw new IllegalStateException("Should be no redirect to login")
      override def redirectToLogin(implicit request: Request[_]) = Future.successful(Unauthorized)
    }

    override def redirectToLogin(implicit request: Request[_]) = Future.successful(Unauthorized)
  }

  class AnyAuthProviderTestCase(authProviderInSession: Option[String]) {
    val request = authProviderInSession.foldLeft(FakeRequest())((req, provider) => req.withSession(SessionKeys.authProvider -> provider))
    def anyAuthProviderForTest: AnyAuthenticationProvider = new AnyAuthenticationProviderForTest()
    val handleNotAuthenticated = anyAuthProviderForTest.handleNotAuthenticated(request)
  }

  class AnyAuthProviderTestCaseNoRedirect(authProviderInSession: Option[String])
    extends AnyAuthProviderTestCase(authProviderInSession){
    override def anyAuthProviderForTest: AnyAuthenticationProvider = new AnyAuthenticationProviderForTestNoRedirect()
  }
}
