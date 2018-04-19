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

package uk.gov.hmrc.play.frontend.auth.connectors.domain

import play.api.libs.json._
import uk.gov.hmrc.play.test.UnitSpec

import scala.reflect.ClassTag

class AgentModelSpec extends UnitSpec {

  "AgentRole Json marshalling and unmarshalling" should {

    "properly handle the admin role" in new SetUp {

      val agentAccount: AgentAccount = unmarshalAgentAccountWith("admin")
      agentAccount.agentUserRole should be theSameInstanceAs AgentAdmin

      val agentAccountJson: JsValue = Json.toJson(agentAccount)
      (agentAccountJson \ "agentUserRole").as[JsString].value shouldBe "admin"
    }

    "properly handle the assistant role" in new SetUp {

      val agentAccount: AgentAccount = unmarshalAgentAccountWith("assistant")
      agentAccount.agentUserRole should be theSameInstanceAs AgentAssistant

      val agentAccountJson: JsValue = Json.toJson(agentAccount)
      (agentAccountJson \ "agentUserRole").as[JsString].value shouldBe "assistant"
    }

    "fail to unmarshal an invalid role" in new SetUp {

      an [Exception] should be thrownBy unmarshalAgentAccountWith("invalid")
    }
  }

  "AgentRole evaluation of required role when working with an Administrator" should {

    "Satisfy requirement of Administrator" in {
      AgentAdmin.satisfiesRequiredRole(AgentAdmin) shouldBe true
    }

    "Satisfy requirement of Assistant" in {
      AgentAdmin.satisfiesRequiredRole(AgentAssistant) shouldBe true
    }
  }

  "AgentRole evaluation of required role when working with an Assistant" should {

    "Satisfy requirement of Administrator" in {
      AgentAssistant.satisfiesRequiredRole(AgentAdmin) shouldBe false
    }

    "Satisfy requirement of Assistant" in {
      AgentAssistant.satisfiesRequiredRole(AgentAssistant) shouldBe true
    }
  }

  class SetUp {

    def unmarshalAgentAccountWith(role: String) = {
      unmarshalJson[AgentAccount](agentAccountWithRoleJsonString(role))
    }

    def unmarshalJson[T](json: JsValue)(implicit t: ClassTag[T], reads: Reads[T]) = {
      val result = json.validate[T]
      result match {
        case s: JsSuccess[T] => s.get
        case e: JsError => throw new Exception("Invalid services " + JsError.toFlatJson(e).toString())
      }
    }

    def agentAccountWithRoleJsonString(role: String): JsValue = {
      val agentAccount =
        s"""
        |	{
        | 	"link": "/agent/ABCDEF123456",
        |		"agentCode": "ABCDEF123456",
        |		"agentUserId": "ZMOQ1hrrP-9ZmnFw0kIA5vlc-mo",
        |		"agentUserRole": "$role",
        |		"payeReference": "HZ1234",
        |		"agentBusinessUtr": "JARN1234567"
        |	}
      """.stripMargin
      Json.parse(agentAccount)
    }
  }
}
