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

package uk.gov.hmrc.play.frontend.auth.connectors

import uk.gov.hmrc.play.frontend.auth.connectors.domain.Authority
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpReads}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

trait AuthConnector {

  val serviceUrl: String

  def http: HttpGet

  def currentAuthority(implicit hc: HeaderCarrier): Future[Option[Authority]] = {
    http.GET[Authority](s"$serviceUrl/auth/authority").map(Some.apply) // Option return is legacy of previous http library now baked into this class's api
  }
  
  def get[T](uri: String)(implicit hc: HeaderCarrier, reads: HttpReads[T]): Future[T] = {
	// currently affordances are sent inconsistently from auth, some URLs are absolute while others are not
    val fullUri = if (uri.startsWith("http")) uri else s"$serviceUrl$uri"
    http.GET[T](fullUri)
  }
  
}
