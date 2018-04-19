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

import uk.gov.hmrc.play.test.UnitSpec

class DelegationStateSpec extends UnitSpec {

  "The toString method" should {

    "return On for DelegationOn" in {
      DelegationOn.toString shouldBe "On"
    }

    "return Off for DelegationOff" in {
      DelegationOff.toString shouldBe "Off"
    }
  }

  "The apply method" should {

    "return DelegationOn if the value is On" in {
      DelegationState(Some("On")) shouldBe DelegationOn
    }

    "return DelegationOff for any other string value" in {
      DelegationState(Some("Off")) shouldBe DelegationOff
      DelegationState(Some("on")) shouldBe DelegationOff
      DelegationState(Some(" On ")) shouldBe DelegationOff
      DelegationState(Some("sdfdsfdsf")) shouldBe DelegationOff
      DelegationState(Some("")) shouldBe DelegationOff
      DelegationState(Some("   ")) shouldBe DelegationOff
    }

    "return DelegationOff if None is passed in" in {
      DelegationState(None) shouldBe DelegationOff
    }
  }
}
