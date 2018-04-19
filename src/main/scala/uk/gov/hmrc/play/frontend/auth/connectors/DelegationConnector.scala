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

package uk.gov.hmrc.play.frontend.auth.connectors

import play.api.libs.json.Json
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.frontend.auth.{DelegationContext, DelegationData, Link, TaxIdentifiers}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait DelegationConnector {

  protected def serviceUrl: String

  protected def http: CoreGet with CorePut with CoreDelete

  private implicit val linkFormat = Json.format[Link]
  private implicit val delegationDataFormat = Json.format[DelegationData]
  private implicit val taxIdentifiersFormat = Json.format[TaxIdentifiers]
  private implicit val delegationContextFormat = Json.format[DelegationContext]

  private def delegationUrl(oid: String): String = s"$serviceUrl/oid/$oid"

  val responseHandler = new HttpReads[Option[DelegationData]] {
    override def read(method: String, url: String, response: HttpResponse): Option[DelegationData] = {
      response.status match {
        case 200 => Try(response.json.as[DelegationData]) match {
          case Success(data) => Some(data)
          case Failure(e) => throw DelegationServiceException("Unable to parse response", method, url, e)
        }
        case 404 => None
        case unexpectedStatus => throw DelegationServiceException(s"Unexpected response code '$unexpectedStatus'", method, url)
      }
    }
  }

  def getDelegationData(oid: String, responseHandler: HttpReads[Option[DelegationData]] = responseHandler)(implicit hc: HeaderCarrier): Future[Option[DelegationData]] = {
    http.GET[Option[DelegationData]](delegationUrl(oid))
  }

  def startDelegation(oid: String, delegationContext: DelegationContext)(implicit hc: HeaderCarrier): Future[Unit] = {

      http.PUT[DelegationContext, HttpResponse](delegationUrl(oid), delegationContext).map { (response: HttpResponse) =>
      response.status match {
        case 201 => ()
        case unexpectedStatus => throw DelegationServiceException(s"Unexpected response code '$unexpectedStatus'", "PUT", delegationUrl(oid))
      }
    }
  }

  def endDelegation(oid: String)(implicit hc: HeaderCarrier): Future[Unit] = {

    case class DeletionResponse(override val status: Int, cause: Option[Throwable] = None) extends HttpResponse

    val deletionResponse = http.DELETE[HttpResponse](delegationUrl(oid)).map(r => DeletionResponse(r.status)).recover {
      case e: HttpException => DeletionResponse(e.responseCode, Some(e))
      case e: Upstream4xxResponse => DeletionResponse(e.upstreamResponseCode, Some(e))
      case e: Upstream5xxResponse => DeletionResponse(e.upstreamResponseCode, Some(e))
    }

    deletionResponse.map {
      case DeletionResponse(204 | 404, _) => ()
      case DeletionResponse(unexpectedStatus, cause) =>
        throw DelegationServiceException(s"Unexpected response code '$unexpectedStatus'", "DELETE", delegationUrl(oid), cause.orNull)
    }
  }
}

case class DelegationServiceException(message: String, method: String, url: String, cause: Throwable = null)
  extends RuntimeException(s"$message: $method $url", cause)
