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

package uk.gov.hmrc.play.frontend.auth.connectors.domain

import org.joda.time.DateTime
import play.api.libs.json._
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.domain._
import uk.gov.hmrc.play.controllers.RestFormats
import uk.gov.hmrc.time.DateTimeUtils

import scala.util.{Failure, Success, Try}


sealed abstract class ConfidenceLevel(val level: Int) extends Ordered[ConfidenceLevel] {
  def compare(that: ConfidenceLevel) = this.level.compare(that.level)
  override val toString = level.toString
}

object ConfidenceLevel {
  case object L500 extends ConfidenceLevel(500)
  case object L300 extends ConfidenceLevel(300)
  case object L200 extends ConfidenceLevel(200)
  case object L100 extends ConfidenceLevel(100)
  case object L50 extends ConfidenceLevel(50)
  case object L0 extends ConfidenceLevel(0)

  val all = Set(L0, L50, L100, L200, L300, L500)

  def fromInt(level: Int): ConfidenceLevel = level match {
    case 500 => L500
    case 300 => L300
    case 200 => L200
    case 100 => L100
    case 50 => L50
    case 0   => L0
    case _   => throw new NoSuchElementException(s"Illegal confidence level: $level")
  }

  implicit val format: Format[ConfidenceLevel] = {
    val reads = Reads[ConfidenceLevel] { json =>
      Try { fromInt(json.as[Int]) } match {
        case Success(level) => JsSuccess(level)
        case Failure(ex) => JsError(ex.getMessage)
      }
    }
    val writes = Writes[ConfidenceLevel] { level => JsNumber(level.level) }
    Format(reads, writes)
  }

  implicit val Binder = new QueryStringBindable[ConfidenceLevel] {

     override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ConfidenceLevel]] = {
       val boundInt = QueryStringBindable.bindableInt.bind(key, params)
       boundInt map {
         case Right(intValue) => try {
           Right(ConfidenceLevel.fromInt(intValue))
         } catch {
           case e: NoSuchElementException => Left(s"'$intValue' is not a valid confidenceLevel")
         }
         case Left(value) => Left(value)
       }
     }

     def unbind(key: String, value: ConfidenceLevel): String = QueryStringBindable.bindableInt.unbind(key, value.level)
   }
}

sealed abstract class CredentialStrength(val name: String)

object CredentialStrength {

  case object Strong extends CredentialStrength("strong")
  case object Weak extends CredentialStrength("weak")
  case object None extends CredentialStrength("none")

  val fromName: String => CredentialStrength = Seq(Strong, Weak, None).map(c => c.name -> c).toMap

  implicit val format = {
    val reads = new Reads[CredentialStrength] {
      override def reads(json: JsValue) =
        try {
          JsSuccess(fromName(json.as[String]))
        } catch {
          case _ : Throwable => JsError()
        }
    }
    val writes = new Writes[CredentialStrength] {
      override def writes(o: CredentialStrength) = new JsString(o.name)
    }
    Format(reads, writes)
  }

}

case class Authority(uri: String,
                     accounts: Accounts,
                     loggedInAt: Option[DateTime],
                     previouslyLoggedInAt: Option[DateTime],
                     credentialStrength: CredentialStrength,
                     confidenceLevel: ConfidenceLevel,
                     userDetailsLink: Option[String],
                     enrolments: Option[String],
                     ids: Option[String])

object Authority {
  implicit val format = {
    implicit val dateFormat = RestFormats.dateTimeFormats
    implicit val accountsFormat = Accounts.format
    implicit val confidenceLevelFormat = ConfidenceLevel.format
    Json.format[Authority]
  }
}

case class IdaPid(pid: String)

object IdaPid {
  implicit val format = {
    Json.format[IdaPid]
  }
}


sealed trait AgentRole {
  def satisfiesRequiredRole(role: AgentRole): Boolean
}

object AgentRole {

  implicit def format: Format[AgentRole] = new Format[AgentRole] {

    override def reads(json: JsValue): JsResult[AgentRole] = {
      json.as[JsString].value match {
        case "admin" => JsSuccess(AgentAdmin)
        case "assistant" => JsSuccess(AgentAssistant)
        case other => throw new Exception(s"Unexpected role: $other")
      }
    }

    override def writes(role: AgentRole): JsValue = {
      role match {
        case AgentAdmin => JsString("admin")
        case AgentAssistant => JsString("assistant")
        case other => throw new Exception(s"Unexpected role: $other")
      }
    }
  }
}

object AgentAdmin extends AgentRole {
  def satisfiesRequiredRole(role: AgentRole): Boolean = {
    role match {
      case AgentAdmin => true
      case AgentAssistant => true
    }
  }
}

object AgentAssistant extends AgentRole {
  def satisfiesRequiredRole(role: AgentRole): Boolean = {
    role match {
      case AgentAdmin => false
      case AgentAssistant => true
    }
  }
}


case class AgentAccount(link: String, agentCode: AgentCode, agentUserId: AgentUserId,
                        agentUserRole: AgentRole,
                        payeReference: Option[PayeAgentReference],
                        agentBusinessUtr: Option[AgentBusinessUtr] = None) extends Account

object AgentAccount {
  implicit val format = Json.format[AgentAccount]
}


case class Accounts(paye: Option[PayeAccount] = None,
                    sa: Option[SaAccount] = None,
                    ct: Option[CtAccount] = None,
                    vat: Option[VatAccount] = None,
                    epaye: Option[EpayeAccount] = None,
                    agent: Option[AgentAccount] = None,
                    tai: Option[TaxForIndividualsAccount] = None,
                    taxsAgent: Option[TaxSummariesAgentAccount] = None,
                    tcs: Option[TaxCreditServiceAccount] = None,
                    ei: Option[EIAccount] = None,
                    org: Option[OrgAccount] = None,
                    ated: Option[AtedAccount] = None,
                    awrs: Option[AwrsAccount] = None,
                    psa: Option[PsaAccount] = None,
                    psp: Option[PspAccount] = None,
                    iht: Option[IhtAccount] = None) {
  def toMap = Map() ++
    sa.map("saUtr" -> _.utr.utr).toMap ++
    vat.map("vrn" -> _.vrn.vrn).toMap ++
    ct.map("ctUtr" -> _.utr.utr).toMap ++
    epaye.map("empRef" -> _.empRef.toString).toMap ++
    paye.map("nino" -> _.nino.nino).toMap ++
    org.map("org" -> _.org.org).toMap ++
    ei.map("empRef" -> _.empRef.toString).toMap ++
    agent.map("agentCode" -> _.agentCode).toMap ++
    ated.map("atedUtr" -> _.utr.utr).toMap ++
    awrs.map("awrsUtr" -> _.utr.utr).toMap ++
    psa.map("psaId" -> _.id.id).toMap ++
    psp.map("pspId" -> _.id.id).toMap ++
    taxsAgent.map("uar" -> _.uar.uar).toMap ++
    iht.map("iht" -> _.nino.nino).toMap
}

object Accounts {
  implicit val format = {
    implicit val payeFormat = Json.format[PayeAccount]
    implicit val saFormat = Json.format[SaAccount]
    implicit val ctFormat = Json.format[CtAccount]
    implicit val vatFormat = Json.format[VatAccount]
    implicit val epayeFormat = Json.format[EpayeAccount]
    implicit val tfiFormat = Json.format[TaxForIndividualsAccount]
    implicit val taxsAgentFormat = Json.format[TaxSummariesAgentAccount]
    implicit val tcsFormat = Json.format[TaxCreditServiceAccount]
    implicit val ihtFormat = Json.format[IhtAccount]
    implicit val ninoFormat = Json.format[Nino]
    implicit val orgFormat = Json.format[OrgAccount]
    implicit val agentFormat = Json.format[AgentAccount]
    implicit val eiFormat = Json.format[EIAccount]
    implicit val psaFormat = Json.format[PsaAccount]
    implicit val pspFormat = Json.format[PspAccount]
    implicit val atedFormat = Json.format[AtedAccount]
    implicit val awrsFormat = Json.format[AwrsAccount]
    Json.format[Accounts]
  }
}

case class PayeAccount(link: String, nino: Nino) extends Account

case class TaxForIndividualsAccount(link: String, nino: Nino) extends Account

case class TaxCreditServiceAccount(link: String, nino: Nino) extends Account

case class SaAccount(link: String, utr: SaUtr) extends Account

case class CtAccount(link: String, utr: CtUtr) extends Account

case class VatAccount(link: String, vrn: Vrn) extends Account

case class EpayeAccount(link: String, empRef: EmpRef) extends Account

case class TaxSummariesAgentAccount(link: String, uar: Uar) extends Account

case class OrgAccount(link: String, org: Org) extends Account

case class EIAccount(link: String, empRef: EmpRef) extends Account

case class AtedAccount(link: String, utr: AtedUtr) extends Account

case class AwrsAccount(link: String, utr: AwrsUtr) extends Account

case class PsaAccount(link: String, id: PsaId) extends Account

case class PspAccount(link: String, id: PspId) extends Account

case class IhtAccount(link: String, nino: Nino) extends Account

sealed abstract class Account {
  val link: String
}

case class CreationAndLastModifiedDetail(createdAt: DateTime, lastUpdated: DateTime)

object CreationAndLastModifiedDetail {
  def apply(): CreationAndLastModifiedDetail = CreationAndLastModifiedDetail(DateTimeUtils.now, DateTimeUtils.now)
}
