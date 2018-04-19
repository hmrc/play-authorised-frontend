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

import uk.gov.hmrc.domain.CtUtr
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{Accounts, CtAccount}
import uk.gov.hmrc.play.test.UnitSpec

class DelegationDataSpec extends UnitSpec {

  "The attorney for a delegation data" should {

    val principalName = "principal"
    val accounts = Accounts(ct = Some(CtAccount("/link", CtUtr("4534231201"))))
    val link = Link("/blah", "Text")

    "combine the attorneyName and the link" in {
      DelegationData(principalName, "attorney", accounts, link).attorney shouldBe Attorney("attorney", link)
    }
  }
}
