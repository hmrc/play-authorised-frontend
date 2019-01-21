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

import play.api.mvc.Session
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.test.UnitSpec

class UserSessionDataSpec extends UnitSpec {

  "The userCredentials method" should {

    "return the correct UserCredentials object for the session data" in {

      UserSessionData(Some("abc"), Some("token"), Some("name"), DelegationOff).userCredentials shouldBe UserCredentials(Some("abc"), Some("token"))

      UserSessionData(Some("abc"), None, Some("name"), DelegationOff).userCredentials shouldBe UserCredentials(Some("abc"), None)

      UserSessionData(None, Some("blah"), Some("name"), DelegationOff).userCredentials shouldBe UserCredentials(None, Some("blah"))

      UserSessionData(None, None, Some("name"), DelegationOff).userCredentials shouldBe UserCredentials(None, None)
    }
  }

  "Constructing a UserSessionData from the session" should {

    val DelegationStateKey = "delegationState"

    "pull out the correct values and create the object if all values are present" in {

      val session = Session(Map(
        SessionKeys.userId -> "/blah/bluh/ba",
        SessionKeys.token -> "asdsadads",
        SessionKeys.name -> "Bob User",
        DelegationStateKey -> "On"
      ))

      UserSessionData(session) shouldBe UserSessionData(Some("/blah/bluh/ba"), Some("asdsadads"), Some("Bob User"), DelegationOn)
    }

    "set appropriate values if data is missing" in {
      UserSessionData(Session(Map.empty)) shouldBe UserSessionData(None, None, None, DelegationOff)
    }

    "cope with a mix of values" in {

      val session1 = Session(Map(
        SessionKeys.userId -> "/blah/bluh/ba",
        DelegationStateKey -> "Off"
      ))

      UserSessionData(session1) shouldBe UserSessionData(Some("/blah/bluh/ba"), None, None, DelegationOff)

      val session2 = Session(Map(
        SessionKeys.token -> "asdsadads",
        SessionKeys.name -> "Bob User",
        DelegationStateKey -> "On"
      ))

      UserSessionData(session2) shouldBe UserSessionData(None, Some("asdsadads"), Some("Bob User"), DelegationOn)

      val session3 = Session(Map(
        SessionKeys.userId -> "/blah/bluh/ba",
        SessionKeys.name -> "Bob User"
      ))

      UserSessionData(session3) shouldBe UserSessionData(Some("/blah/bluh/ba"), None, Some("Bob User"), DelegationOff)
    }
  }
}
