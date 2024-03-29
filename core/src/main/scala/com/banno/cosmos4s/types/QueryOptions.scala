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

import com.azure.cosmos.models.{CosmosQueryRequestOptions, PartitionKey}

final class QueryOptions private (
    maxDegreeOfParallelism: Option[Int],
    maxBufferedItemCount: Option[Int],
    partitionKey: Option[PartitionKey],
    private val indexMetricsEnabled: Option[Boolean]
) extends Serializable {

  private[this] def copy(
      maxDegreeOfParallelism: Option[Int] = maxDegreeOfParallelism,
      maxBufferedItemCount: Option[Int] = maxBufferedItemCount,
      partitionKey: Option[PartitionKey] = partitionKey,
      indexMetricsEnabled: Option[Boolean] = indexMetricsEnabled
  ): QueryOptions =
    new QueryOptions(
      maxDegreeOfParallelism,
      maxBufferedItemCount,
      partitionKey,
      indexMetricsEnabled
    )

  def withMaxDegreeOfParallelism(value: Option[Int]): QueryOptions =
    this.copy(maxDegreeOfParallelism = value)

  def withMaxBufferedItemCount(value: Option[Int]): QueryOptions =
    this.copy(maxBufferedItemCount = value)

  def withPartitionKey(value: Option[PartitionKey]): QueryOptions =
    this.copy(partitionKey = value)

  def enableIndexMetrics: QueryOptions = copy(indexMetricsEnabled = Some(true))

  private val getMaxDegreeOfParallelism: Option[Int] = maxDegreeOfParallelism
  private val getMaxBufferedItemCount: Option[Int] = maxBufferedItemCount
  private def getPartitionKey: Option[PartitionKey] = partitionKey

  override def toString: String = s"QueryOptions($maxDegreeOfParallelism, $maxBufferedItemCount)"

  override def equals(o: Any): Boolean =
    o match {
      case x: QueryOptions =>
        (this.maxDegreeOfParallelism == x.getMaxDegreeOfParallelism) &&
        (this.maxBufferedItemCount == x.getMaxBufferedItemCount)
        (this.partitionKey == x.getPartitionKey) &&
        (this.indexMetricsEnabled == x.indexMetricsEnabled)
      case _ => false
    }

  override def hashCode: Int =
    37 * (37 * (17 + indexMetricsEnabled.## + (37 * (17 + partitionKey.## + (37 * (17 + maxDegreeOfParallelism.##) + maxBufferedItemCount.##)))))

  private[cosmos4s] def build(): CosmosQueryRequestOptions = {
    val cosmosQueryOptions = new CosmosQueryRequestOptions()
    maxDegreeOfParallelism.foreach(cosmosQueryOptions.setMaxDegreeOfParallelism)
    maxBufferedItemCount.foreach(cosmosQueryOptions.setMaxBufferedItemCount)
    partitionKey.foreach(cosmosQueryOptions.setPartitionKey)
    indexMetricsEnabled.foreach(cosmosQueryOptions.setIndexMetricsEnabled)
    cosmosQueryOptions
  }
}

object QueryOptions {
  val default: QueryOptions = new QueryOptions(None, None, None, None)
}
