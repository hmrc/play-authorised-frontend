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

import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class CompositeVisibilityPredicateSpec extends UnitSpec {

  val passes = new PageVisibilityPredicate{
    override def apply(v1: AuthContext, v2: Request[AnyContent]): Future[PageVisibilityResult] = Future.successful(PageIsVisible)
  }
  val fails = new PageVisibilityPredicate{
    override def apply(v1: AuthContext, v2: Request[AnyContent]): Future[PageVisibilityResult] = Future.successful(PageBlocked(Future.successful(NotFound)))
  }
  val blowsUp = new PageVisibilityPredicate{
    override def apply(v1: AuthContext, v2: Request[AnyContent]): Future[PageVisibilityResult] = Future.failed(new RuntimeException)
  }

  
  def testWith(chiln: PageVisibilityPredicate*) = new CompositePageVisibilityPredicate {
    override def children: Seq[PageVisibilityPredicate] = chiln
  }
  
  "A composite predicate" should {

    "pass if everything passes" in {
      val passLots = testWith(passes, passes, passes, passes)
      val r = await(passLots(null, FakeRequest()))
      r.isVisible shouldBe true
    }

    "pass if it is empty" in {
      val passLots = testWith()
      val r = await(passLots(null, FakeRequest()))
      r.isVisible shouldBe true
    }

    "find the fist failing child" in {
      val passFail = testWith(passes, fails)
      val r = await(passFail(null, FakeRequest()))
      r.isVisible shouldBe false
      status(r.nonVisibleResult) shouldBe 404
    }

    "not call subsequent children once a failing case has been found" in {
      val passFailBlow = testWith(passes, fails, blowsUp)
      val r = await(passFailBlow(null, FakeRequest()))
      r.isVisible shouldBe false
    }

    "fail with the first failing child that is encountered" in {
      val passBlow = testWith(passes, blowsUp, fails)
      intercept[RuntimeException] {
        await(passBlow(null, FakeRequest()))
      }
    }
  }
}
