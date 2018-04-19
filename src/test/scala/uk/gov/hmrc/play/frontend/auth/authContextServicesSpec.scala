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

import org.joda.time.{DateTime, DateTimeZone}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.domain.{CtUtr, Nino, SaUtr}
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import uk.gov.hmrc.play.frontend.auth.connectors.domain.CredentialStrength
import uk.gov.hmrc.play.frontend.auth.connectors.{AuthConnector, DelegationConnector, domain}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Matchers.{any => mockitoAny}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AuthContextServiceWithDelegationEnabledSpec extends UnitSpec with MockitoSugar {

  class TestSetup(val delegationSessionFlag: DelegationState, val returnDataFromDelegationService: Boolean = true)

  case object DelegationOffButDataAvailable extends TestSetup(DelegationOff)

  case object DelegationOnButNoData extends TestSetup(DelegationOn, returnDataFromDelegationService = false)

  case object DelegationOnAndDataAvailable extends TestSetup(DelegationOn)

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "When the userId in the session is missing, the currentAuthContext method" should {

    "return None" in new TestCase(DelegationOnAndDataAvailable) {

      val sessionData = UserSessionData(
        userId = None,
        governmentGatewayToken = Some(session.governmentGatewayToken),
        name = Some(session.name),
        delegationState = DelegationOn
      )

      await(service.currentAuthContext(sessionData)) shouldBe None

      verifyZeroInteractions(mockAuthConnector, mockDelegationConnector)
    }
  }

  "When the delegation session flag is 'Off', even if the delegation data is available, the currentAuthContext method" should {
    behaveAsExpectedWithoutDelegation(DelegationOffButDataAvailable)
    returnNoneIfTheAuthorityIsMissingOrInvalid(DelegationOffButDataAvailable)
  }

  "When the delegation session flag is 'On', but no delegation data is available, the currentAuthContext method" should {
    behaveAsExpectedWithoutDelegation(DelegationOnButNoData)
    returnNoneIfTheAuthorityIsMissingOrInvalid(DelegationOnButNoData)
  }

  "When the delegation session flag is 'On', and delegation data is available, the currentAuthContext method" should {

    "combine the session data with the current authority and delegation data to create the AuthContext" in new TestCase(DelegationOnAndDataAvailable) {

      when(mockAuthConnector.currentAuthority).thenReturn(Future.successful(Some(userAtKeyboard.authority)))

      val sessionData = UserSessionData(
        userId = Some(session.userId),
        governmentGatewayToken = Some(session.governmentGatewayToken),
        name = Some(session.name),
        delegationState = DelegationOn
      )

      val authContext = await(service.currentAuthContext(sessionData))

      authContext shouldBe Some(
        AuthContext(
          user = LoggedInUser(
            session.userId,
            userAtKeyboard.loggedInAt,
            userAtKeyboard.previouslyLoggedInAt,
            Some(session.governmentGatewayToken),
            CredentialStrength.Strong,
            ConfidenceLevel.L500,
            session.oid),
          principal = Principal(Some(delegationData.principalName), delegationData.accounts),
          attorney = Some(Attorney(delegationData.attorneyName, delegationData.link)),
          userDetailsUri = Some("/user-details/1234567890"),
          enrolmentsUri = Some("/auth/1234567/enrolments"),
          idsUri = Some("/auth/1234567/ids")
        ))
    }

    "create the correct AuthContext if the governmentGatewayToken passed in is None" in new TestCase(DelegationOnAndDataAvailable) {

      when(mockAuthConnector.currentAuthority).thenReturn(Future.successful(Some(userAtKeyboard.authority)))

      val sessionData = UserSessionData(
        userId = Some(session.userId),
        governmentGatewayToken = None,
        name = Some(session.name),
        delegationState = DelegationOn
      )

      val authContext = await(service.currentAuthContext(sessionData))

      authContext shouldBe Some(
        AuthContext(
          user = LoggedInUser(
            session.userId,
            userAtKeyboard.loggedInAt,
            userAtKeyboard.previouslyLoggedInAt,
            None,
            CredentialStrength.Strong,
            ConfidenceLevel.L500,
            session.oid),
          principal = Principal(Some(delegationData.principalName), delegationData.accounts),
          attorney = Some(Attorney(delegationData.attorneyName, delegationData.link)),
          userDetailsUri = Some("/user-details/1234567890"),
          enrolmentsUri = Some("/auth/1234567/enrolments"),
          idsUri = Some("/auth/1234567/ids")
        ))
    }

    returnNoneIfTheAuthorityIsMissingOrInvalid(DelegationOnAndDataAvailable)
  }

  private def behaveAsExpectedWithoutDelegation(testSetup: TestSetup) = {

    "combine the session data with the current authority to create the AuthContext" in new TestCase(testSetup) {

      when(mockAuthConnector.currentAuthority).thenReturn(Future.successful(Some(userAtKeyboard.authority)))

      val sessionData = UserSessionData(
        userId = Some(session.userId),
        governmentGatewayToken = Some(session.governmentGatewayToken),
        name = Some(session.name),
        delegationState = testSetup.delegationSessionFlag
      )

      val authContext = await(service.currentAuthContext(sessionData))

      authContext shouldBe Some(
        AuthContext(
          user = LoggedInUser(
            session.userId,
            userAtKeyboard.loggedInAt,
            userAtKeyboard.previouslyLoggedInAt,
            Some(session.governmentGatewayToken),
            CredentialStrength.Strong,
            ConfidenceLevel.L500,
            session.oid),
          principal = Principal(Some(session.name), userAtKeyboard.accounts),
          attorney = None,
          userDetailsUri = Some("/user-details/1234567890"),
          enrolmentsUri = Some("/auth/1234567/enrolments"),
          idsUri = Some("/auth/1234567/ids")
        ))
    }

    "create the correct AuthContext if the governmentGatewayToken passed in is None" in new TestCase(testSetup) {

      when(mockAuthConnector.currentAuthority).thenReturn(Future.successful(Some(userAtKeyboard.authority)))

      val sessionData = UserSessionData(
        userId = Some(session.userId),
        governmentGatewayToken = None,
        name = Some(session.name),
        delegationState = testSetup.delegationSessionFlag
      )

      val authContext = await(service.currentAuthContext(sessionData))

      authContext shouldBe Some(
        AuthContext(
          user = LoggedInUser(
            session.userId,
            userAtKeyboard.loggedInAt,
            userAtKeyboard.previouslyLoggedInAt,
            None,
            CredentialStrength.Strong,
            ConfidenceLevel.L500,
            session.oid),
          principal = Principal(Some(session.name), userAtKeyboard.accounts),
          attorney = None,
          userDetailsUri = Some("/user-details/1234567890"),
          enrolmentsUri = Some("/auth/1234567/enrolments"),
          idsUri = Some("/auth/1234567/ids")
        ))
    }

    "create the correct AuthContext if the nameFromSession passed in is None" in new TestCase(testSetup) {

      when(mockAuthConnector.currentAuthority).thenReturn(Future.successful(Some(userAtKeyboard.authority)))

      val sessionData = UserSessionData(
        userId = Some(session.userId),
        governmentGatewayToken = Some(session.governmentGatewayToken),
        name = None,
        delegationState = testSetup.delegationSessionFlag
      )

      val authContext = await(service.currentAuthContext(sessionData))

      authContext shouldBe Some(
        AuthContext(
          user = LoggedInUser(
            session.userId,
            userAtKeyboard.loggedInAt,
            userAtKeyboard.previouslyLoggedInAt,
            Some(session.governmentGatewayToken),
            CredentialStrength.Strong,
            ConfidenceLevel.L500,
            session.oid),
          principal = Principal(None, userAtKeyboard.accounts),
          attorney = None,
          userDetailsUri = Some("/user-details/1234567890"),
          enrolmentsUri = Some("/auth/1234567/enrolments"),
          idsUri = Some("/auth/1234567/ids")
        ))
    }
  }

  private def returnNoneIfTheAuthorityIsMissingOrInvalid(testSetup: TestSetup) = {

    "return None if the userId passed in does not match the uri of the Authority" in new TestCase(testSetup) {

      val oid = "somethingelse"

      when(mockAuthConnector.currentAuthority).thenReturn(Future.successful(Some(userAtKeyboard.authority)))
      when(mockDelegationConnector.getDelegationData(oid)).thenReturn(Future.successful(Some(delegationData)))

      val sessionData = UserSessionData(
        userId = Some(s"/auth/oid/$oid"),
        governmentGatewayToken = Some(session.governmentGatewayToken),
        name = None,
        delegationState = testSetup.delegationSessionFlag
      )

      await(service.currentAuthContext(sessionData)) shouldBe None
    }

  }

  private abstract class TestCase(testSetup: TestSetup) extends AuthContextServiceTestCase {

    val mockDelegationConnector: DelegationConnector = mock[DelegationConnector]

    val responseHandlerMock: HttpReads[Option[DelegationData]] = mock[HttpReads[Option[DelegationData]]]

    val delegationData = DelegationData(
      principalName = "Bill Principal",
      attorneyName = "Brian Agent",
      accounts = domain.Accounts(ct = Some(domain.CtAccount("/some/path", CtUtr("1234554321")))),
      link = Link("/some/url", "Back to your dashboard")
    )

    if (testSetup.returnDataFromDelegationService) {
      when(mockDelegationConnector.getDelegationData(mockitoAny[String], mockitoAny[HttpReads[Option[DelegationData]]])(mockitoAny[HeaderCarrier])).thenReturn(Future.successful(Some(delegationData)))
    } else {
      when(mockDelegationConnector.getDelegationData(mockitoAny[String], mockitoAny[HttpReads[Option[DelegationData]]])(mockitoAny[HeaderCarrier])).thenReturn(Future.successful(None))
    }

    val service = new AuthContextService with DelegationEnabled {
      override protected val authConnector = mockAuthConnector
      override protected val delegationConnector = mockDelegationConnector
    }
  }

}


class AuthContextServiceDisallowingDelegationSpec extends UnitSpec with MockitoSugar {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  "When the userId in the session is missing, the currentAuthContext method" should {

    "return None" in new TestCase {

      val sessionData = UserSessionData(
        userId = None,
        governmentGatewayToken = Some(session.governmentGatewayToken),
        name = Some(session.name),
        delegationState = DelegationOff
      )

      await(service.currentAuthContext(sessionData)) shouldBe None

      verifyZeroInteractions(mockAuthConnector)
    }
  }

  "When the delegation session flag is 'Off', the currentAuthContext method" should {
    behaveAsExpectedWithoutDelegation(DelegationOff)
    returnNoneIfTheAuthorityIsMissingOrInvalid(DelegationOff)
  }

  "When the delegation session flag is 'On', the currentAuthContext method" should {
    behaveAsExpectedWithoutDelegation(DelegationOn)
    returnNoneIfTheAuthorityIsMissingOrInvalid(DelegationOn)
  }

  private def behaveAsExpectedWithoutDelegation(delegationState: DelegationState) = {

    "combine the session data with the current authority to create the AuthContext" in new TestCase {

      when(mockAuthConnector.currentAuthority).thenReturn(Future.successful(Some(userAtKeyboard.authority)))

      val sessionData = UserSessionData(
        userId = Some(session.userId),
        governmentGatewayToken = Some(session.governmentGatewayToken),
        name = Some(session.name),
        delegationState = delegationState
      )

      val authContext = await(service.currentAuthContext(sessionData))

      authContext shouldBe Some(
        AuthContext(
          user = LoggedInUser(
            session.userId,
            userAtKeyboard.loggedInAt,
            userAtKeyboard.previouslyLoggedInAt,
            Some(session.governmentGatewayToken),
            CredentialStrength.Strong,
            ConfidenceLevel.L500,
            session.oid),
          principal = Principal(Some(session.name), userAtKeyboard.accounts),
          attorney = None,
          userDetailsUri = Some("/user-details/1234567890"),
          enrolmentsUri = Some("/auth/1234567/enrolments"),
          idsUri = Some("/auth/1234567/ids")
        ))
    }

    "create the correct AuthContext if the governmentGatewayToken passed in is None" in new TestCase {

      when(mockAuthConnector.currentAuthority).thenReturn(Future.successful(Some(userAtKeyboard.authority)))

      val sessionData = UserSessionData(
        userId = Some(session.userId),
        governmentGatewayToken = None,
        name = Some(session.name),
        delegationState = delegationState
      )

      val authContext = await(service.currentAuthContext(sessionData))

      authContext shouldBe Some(
        AuthContext(
          user = LoggedInUser(
            session.userId,
            userAtKeyboard.loggedInAt,
            userAtKeyboard.previouslyLoggedInAt,
            None,
            CredentialStrength.Strong,
            ConfidenceLevel.L500,
            session.oid),
          principal = Principal(Some(session.name), userAtKeyboard.accounts),
          attorney = None,
          userDetailsUri = Some("/user-details/1234567890"),
          enrolmentsUri = Some("/auth/1234567/enrolments"),
          idsUri = Some("/auth/1234567/ids")
        ))
    }

    "create the correct AuthContext if the nameFromSession passed in is None" in new TestCase {

      when(mockAuthConnector.currentAuthority).thenReturn(Future.successful(Some(userAtKeyboard.authority)))

      val sessionData = UserSessionData(
        userId = Some(session.userId),
        governmentGatewayToken = Some(session.governmentGatewayToken),
        name = None,
        delegationState = DelegationOff
      )

      val authContext = await(service.currentAuthContext(sessionData))

      authContext shouldBe Some(
        AuthContext(
          user = LoggedInUser(
            session.userId,
            userAtKeyboard.loggedInAt,
            userAtKeyboard.previouslyLoggedInAt,
            Some(session.governmentGatewayToken),
            CredentialStrength.Strong,
            ConfidenceLevel.L500,
            session.oid),
          principal = Principal(None, userAtKeyboard.accounts),
          attorney = None,
          userDetailsUri = Some("/user-details/1234567890"),
          enrolmentsUri = Some("/auth/1234567/enrolments"),
          idsUri = Some("/auth/1234567/ids")
        ))
    }
  }

  private def returnNoneIfTheAuthorityIsMissingOrInvalid(delegationState: DelegationState) = {

    "return None if the userId passed in does not match the uri of the Authority" in new TestCase {

      val oid = "somethingelse"

      when(mockAuthConnector.currentAuthority).thenReturn(Future.successful(Some(userAtKeyboard.authority)))

      val sessionData = UserSessionData(
        userId = Some(s"/auth/oid/$oid"),
        governmentGatewayToken = Some(session.governmentGatewayToken),
        name = None,
        delegationState = delegationState
      )

      await(service.currentAuthContext(sessionData)) shouldBe None
    }
  }

  private abstract class TestCase extends AuthContextServiceTestCase {

    val service = new AuthContextService with DelegationDisabled {
      override protected val authConnector = mockAuthConnector
    }
  }

}

trait AuthContextServiceTestCase extends MockitoSugar {

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  object session {
    val userId = "/auth/1234567"
    val oid = "1234567890"
    val governmentGatewayToken = "token"
    val name = "Dave Agent"
  }

  object userAtKeyboard {

    val loggedInAt = Some(new DateTime(2015, 11, 22, 11, 33, 15, 234, DateTimeZone.UTC))

    val previouslyLoggedInAt = Some(new DateTime(2014, 8, 3, 9, 25, 44, 342, DateTimeZone.UTC))

    val accounts = domain.Accounts(
      paye = Some(domain.PayeAccount(link = "/paye/abc", nino = Nino("AB124512C"))),
      sa = Some(domain.SaAccount(link = "/sa/www", utr = SaUtr("1231231233")))
    )

    val authority = domain.Authority(
      uri = session.userId,
      accounts = accounts,
      loggedInAt = loggedInAt,
      previouslyLoggedInAt = previouslyLoggedInAt,
      credentialStrength = CredentialStrength.Strong,
      confidenceLevel = ConfidenceLevel.L500,
      userDetailsLink = Some("/user-details/1234567890"),
      enrolments = Some("/auth/1234567/enrolments"),
      ids = Some("/auth/1234567/ids"),
      legacyOid = session.oid
    )
  }

}
