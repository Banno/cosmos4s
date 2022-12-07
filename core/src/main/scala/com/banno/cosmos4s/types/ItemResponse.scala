/*
 * Copyright 2020 Jack Henry & Associates, Inc.®
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

package com.banno.cosmos4s.types

import cats.Traverse
import cats.syntax.all._
import com.azure.cosmos.CosmosDiagnostics
import com.azure.cosmos.models.CosmosItemResponse
import scala.jdk.CollectionConverters._
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import cats.Applicative
import cats.Eval

object ItemResponse {

  implicit val traverse: Traverse[ItemResponse] = new Traverse[ItemResponse] {
    def traverse[G[_]: Applicative, A, B](fa: ItemResponse[A])(f: A => G[B]): G[ItemResponse[B]] =
      f(fa.item).map(b => fa.copy(item = b))
    def foldLeft[A, B](fa: ItemResponse[A], b: B)(f: (B, A) => B): B = f(b, fa.item)
    def foldRight[A, B](fa: ItemResponse[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
      f(fa.item, lb)
  }

  def fromCosmosResponse[A, B](transform: Option[A] => B)(
      response: CosmosItemResponse[A]
  ): ItemResponse[B] =
    ItemResponse(
      activityId = response.getActivityId(),
      currentResourceQuotaUsage = response.getCurrentResourceQuotaUsage(),
      diagnostics = response.getDiagnostics(),
      duration = FiniteDuration(response.getDuration().toNanos, TimeUnit.NANOSECONDS),
      eTag = response.getETag(),
      item = transform(Option(response.getItem)),
      maxResourceQuota = response.getMaxResourceQuota(),
      requestCharge = response.getRequestCharge(),
      responseHeaders = response.getResponseHeaders().asScala.toMap,
      sessionToken = response.getSessionToken(),
      statusCode = response.getStatusCode()
    )

}

case class ItemResponse[A](
    activityId: String,
    currentResourceQuotaUsage: String,
    diagnostics: CosmosDiagnostics,
    duration: FiniteDuration,
    eTag: String,
    item: A,
    maxResourceQuota: String,
    requestCharge: Double,
    responseHeaders: Map[String, String],
    sessionToken: String,
    statusCode: Int
)
