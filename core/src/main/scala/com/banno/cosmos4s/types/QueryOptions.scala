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

import com.azure.cosmos.models.CosmosQueryRequestOptions
import cats.implicits._

final class QueryOptions private (
    val maxDegreeOfParallelism: Option[Int],
    val maxBufferedItemCount: Option[Int]
)

object QueryOptions {

  val default: QueryOptions = new QueryOptions(None, None)

  implicit class QueryOptionSyntax(qo: QueryOptions) {
    def withMaxDegreeOfParallelism(value: Int): QueryOptions =
      new QueryOptions(
        maxDegreeOfParallelism = value.some,
        maxBufferedItemCount = qo.maxBufferedItemCount)

    def withMaxBufferedItemCount(value: Int): QueryOptions =
      new QueryOptions(
        maxDegreeOfParallelism = qo.maxDegreeOfParallelism,
        maxBufferedItemCount = value.some)

    private[cosmos4s] def toCosmos: CosmosQueryRequestOptions = {
      val cosmosQueryOptions = new CosmosQueryRequestOptions()
      qo.maxDegreeOfParallelism.foreach(cosmosQueryOptions.setMaxDegreeOfParallelism)
      qo.maxBufferedItemCount.foreach(cosmosQueryOptions.setMaxBufferedItemCount)
      cosmosQueryOptions
    }
  }
}
