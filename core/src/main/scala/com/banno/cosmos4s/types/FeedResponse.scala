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

import collection.JavaConverters._
import com.azure.cosmos.CosmosDiagnostics

object FeedResponse {

  def fromCosmosResponse(response: com.azure.cosmos.models.FeedResponse[_]): FeedResponse =
    new FeedResponse(
      activityId = response.getActivityId(),
      collectionQuota = response.getCollectionQuota(),
      collectionSizeQuota = response.getCollectionSizeQuota(),
      collectionSizeUsage = response.getCollectionSizeUsage(),
      collectionUsage = response.getCollectionUsage(),
      continuationToken = response.getContinuationToken(),
      cosmosDiagnostics = response.getCosmosDiagnostics(),
      currentResourceQuotaUsage = response.getCurrentResourceQuotaUsage(),
      databaseQuota = response.getDatabaseQuota(),
      databaseUsage = response.getDatabaseUsage(),
      maxResourceQuota = response.getMaxResourceQuota(),
      permissionQuota = response.getPermissionQuota(),
      permissionUsage = response.getPermissionUsage(),
      requestCharge = response.getRequestCharge(),
      responseHeaders = response.getResponseHeaders().asScala.toMap,
      sessionToken = response.getSessionToken(),
      storedProceduresQuota = response.getStoredProceduresQuota(),
      storedProceduresUsage = response.getStoredProceduresUsage(),
      triggersQuota = response.getTriggersQuota(),
      triggersUsage = response.getTriggersUsage(),
      userDefinedFunctionsQuota = response.getUserDefinedFunctionsQuota(),
      userDefinedFunctionsUsage = response.getUserDefinedFunctionsUsage(),
      userQuota = response.getUserQuota(),
      userUsage = response.getUserUsage()
    )
}

case class FeedResponse(
    activityId: String,
    collectionQuota: Long,
    collectionSizeQuota: Long,
    collectionSizeUsage: Long,
    collectionUsage: Long,
    continuationToken: String,
    cosmosDiagnostics: CosmosDiagnostics,
    currentResourceQuotaUsage: String,
    databaseQuota: Long,
    databaseUsage: Long,
    maxResourceQuota: String,
    permissionQuota: Long,
    permissionUsage: Long,
    requestCharge: Double,
    responseHeaders: Map[String, String],
    sessionToken: String,
    storedProceduresQuota: Long,
    storedProceduresUsage: Long,
    triggersQuota: Long,
    triggersUsage: Long,
    userDefinedFunctionsQuota: Long,
    userDefinedFunctionsUsage: Long,
    userQuota: Long,
    userUsage: Long
)
