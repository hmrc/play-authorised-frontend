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

package uk.gov.hmrc.play.frontend.auth.connectors

import uk.gov.hmrc.http.{CoreGet, HeaderCarrier, HttpReads, NotFoundException}
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Authority

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.play.frontend.auth.AuthContext

trait AuthConnector {

  val serviceUrl: String

  def http: CoreGet

  def currentAuthority(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Authority]] = {
    http.GET[Authority](s"$serviceUrl/auth/authority").map(Some.apply) // Option return is legacy of previous http library now baked into this class's api
  }
  
  protected def get[T](optUri: Option[String])(implicit hc: HeaderCarrier, reads: HttpReads[T], ec:ExecutionContext): Future[T] = {
    optUri.fold(Future.failed[T](new NotFoundException("Affordance not available in authority"))) { uri =>
      // currently affordances are sent inconsistently from auth, some URLs are absolute while others are not
      val fullUri = if (uri.startsWith("http")) uri else s"$serviceUrl$uri"
      http.GET[T](fullUri)
    }
  }
  
  /** Retrieves the user details that are associated with the specified auth context.
   *  
   *  This method expects a custom Scala class and associated JSON `Reads` for decoding the response
   *  or alternatively allows you to receive the raw JSON if you leave off the type parameter.
   *  
   *  You should only bind to the subset of available user details you are actually using in your
   *  service to avoid tight coupling to auth JSON formats. 
   *  
   *  The JSON format is documented at https://github.tools.tax.service.gov.uk/HMRC/user-details#get-user-detailsidid
   */
  def getUserDetails[T](authContext: AuthContext)(implicit hc: HeaderCarrier, reads: HttpReads[T], ec:ExecutionContext): Future[T] =
    get[T](authContext.userDetailsUri)
    
  /** Retrieves the enrolments that are associated with the specified auth context.
   *  
   *  This method expects a custom Scala class and associated JSON `Reads` for decoding the response
   *  or alternatively allows you to receive the raw JSON if you leave off the type parameter.
   *   
   *  You should only bind to the subset of enrolment data you are actually using in your
   *  service to avoid tight coupling to auth JSON formats. 
   *  
   *  The JSON format is documented at https://github.tools.tax.service.gov.uk/HMRC/auth#enrolments-get
   */
  def getEnrolments[T](authContext: AuthContext)(implicit hc: HeaderCarrier, reads: HttpReads[T], ec:ExecutionContext): Future[T] =
    get[T](authContext.enrolmentsUri)
    
  /** Retrieves the stable identifiers that are associated with the specified auth context.
   *  These identifiers replace the deprecated `userId` and `oid` properties from the `AuthContext`.
   *  
   *  This method expects a custom Scala class and associated JSON `Reads` for decoding the response
   *  or alternatively allows you to receive the raw JSON if you leave off the type parameter.
   *   
   *  You should only bind to the one id you are actually using in your
   *  service to avoid tight coupling to auth JSON formats. 
   *  
   *  The JSON format is documented at https://github.tools.tax.service.gov.uk/HMRC/auth#ids-get
   */
  def getIds[T](authContext: AuthContext)(implicit hc: HeaderCarrier, reads: HttpReads[T], ec:ExecutionContext): Future[T] =
    get[T](authContext.idsUri)
  
}
