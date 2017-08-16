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

package uk.gov.hmrc.play.frontend.auth

import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class ActingAsAttorneyForSpec extends UnitSpec with WithFakeApplication {

  "ActingAsAttorneyFor helper" should {
    "remove the attorney session object when getSessionAndStopActingAsAttorney is invoked" in {

      val actingAttorney = ActingAsAttorneyFor(Some("fred"), Map("NINO" -> "bing"))
      val actingAttorneyJson = Json.toJson(actingAttorney)
      val mySession = ActingAsAttorneyFor.getSessionActingAsAttorneyFor(actingAttorney, FakeRequest().session)
      val newSession=ActingAsAttorneyFor.getSessionAndStopActingAsAttorney(mySession)
      newSession.data should not contain (ActingAsAttorneyFor.ACTING_AS_ATTORNEY_FOR -> actingAttorneyJson.toString())
    }
  }

  "ActingAsAttorneyFor helper" should {
    "add the attorney object to session when getSessionActingAsAttorneyFor is invoked" in {
      val actingAttorneyA = ActingAsAttorneyFor(Some("fred"), Map("NINO" -> "bing"))
      val actingAttorneyJsonA = Json.toJson(actingAttorneyA)
      val mySession = ActingAsAttorneyFor.getSessionActingAsAttorneyFor(actingAttorneyA, FakeRequest().session)
      val newSession=ActingAsAttorneyFor.getSessionActingAsAttorneyFor(actingAttorneyA, mySession)
      newSession.data should contain(ActingAsAttorneyFor.ACTING_AS_ATTORNEY_FOR -> actingAttorneyJsonA.toString())
    }
  }

  "ActingAsAttorneyFor helper" should {
    "replace the name of the json attorney object stored in session when getActingAsAttorneyForUpdateName is invoked" in {
      val actingAttorneyA = ActingAsAttorneyFor(Some("fred"), Map("NINO" -> "bing"))
      val mySession = ActingAsAttorneyFor.getSessionActingAsAttorneyFor(actingAttorneyA, FakeRequest().session)
      val updatedAttorney=ActingAsAttorneyFor.getActingAsAttorneyForUpdateName("new name", mySession)
      val actingAttorneyB = ActingAsAttorneyFor(Some("new name"), Map("NINO" -> "bing"))
      updatedAttorney.get should equal(actingAttorneyB)
    }
  }

  "ActingAsAttorneyFor helper" should {
    "obtain the json object stored in session and return as a case class object" in {
      val actingAttorneyA: ActingAsAttorneyFor = ActingAsAttorneyFor(Some("fred"), Map("NINO" -> "bing"))
      val mySession = ActingAsAttorneyFor.getSessionActingAsAttorneyFor(actingAttorneyA, FakeRequest().session)
      val attorney: Option[ActingAsAttorneyFor] =ActingAsAttorneyFor.getActingAsAttorneyFor(mySession)
      attorney.get should equal(actingAttorneyA)
    }
  }

}
