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

import java.net.URI

import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent._

trait PageVisibilityResult {
  def isVisible: Boolean
  def nonVisibleResult: Future[Result] = Future.successful(NotFound)
}

object PageIsVisible extends PageVisibilityResult {
  override def isVisible: Boolean = true
}

case class PageBlocked(override val nonVisibleResult: Future[Result]) extends PageVisibilityResult {
  override def isVisible: Boolean = false
}

class IdentityConfidencePredicate(requiredConfidenceLevel: ConfidenceLevel, failedConfidenceResult: => Future[Result])
  extends PageVisibilityPredicate {

  def apply(authContext: AuthContext, request: Request[AnyContent]) = Future.successful(new PageVisibilityResult {
    override def isVisible =
      authContext.user.confidenceLevel >= requiredConfidenceLevel

    override def nonVisibleResult = failedConfidenceResult
  })
}

trait CompositePageVisibilityPredicate extends PageVisibilityPredicate {

  def children: Seq[PageVisibilityPredicate]

  override def apply(authContext: AuthContext, request: Request[AnyContent]): Future[PageVisibilityResult] = {
    implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    children.foldLeft[Future[PageVisibilityResult]](Future.successful(PageIsVisible)) { (eventualPriorResult, currentPredicate) =>
      eventualPriorResult.flatMap { priorResult =>
        if (priorResult.isVisible) currentPredicate(authContext, request)
        else eventualPriorResult
      }
    }

  }
}

class UpliftingIdentityConfidencePredicate(requiredConfidenceLevel: ConfidenceLevel, upliftConfidenceUri: URI)
  extends IdentityConfidencePredicate(requiredConfidenceLevel, Future.successful(Redirect(upliftConfidenceUri.toString)))

class NonNegotiableIdentityConfidencePredicate(requiredConfidenceLevel: ConfidenceLevel)
  extends IdentityConfidencePredicate(requiredConfidenceLevel, Future.successful(Forbidden))

private[auth] object WithPageVisibility {


  def apply(predicate: PageVisibilityPredicate, authContext: AuthContext)(action: AuthContext => Action[AnyContent]): Action[AnyContent] =
    Action.async {
      request =>
        implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))
        predicate(authContext, request).flatMap { visible =>
          if (visible.isVisible)
            action(authContext)(request)
          else
            visible.nonVisibleResult
        }
    }
}

object AllowAll extends PageVisibilityPredicate {
  
  def apply(authContext: AuthContext, request: Request[AnyContent]) = Future.successful(new PageVisibilityResult {
    override def isVisible: Boolean = true
  })
}
