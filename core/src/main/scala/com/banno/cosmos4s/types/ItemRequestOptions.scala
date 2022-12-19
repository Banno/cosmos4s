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

import com.azure.cosmos.models.{CosmosItemRequestOptions, IndexingDirective}
import collection.JavaConverters._

final class ItemRequestOptions private (
    private val ifMatchEtag: Option[String],
    private val ifNoneMatchEtag: Option[String],
    private val indexingDirective: Option[IndexingDirective],
    private val postTriggerInclude: Option[List[String]],
    private val preTriggerInclude: Option[List[String]],
    private val sessionToken: Option[String]
) extends Serializable {

  private def copy(
      ifMatchEtag: Option[String] = ifMatchEtag,
      ifNoneMatchEtag: Option[String] = ifNoneMatchEtag,
      indexingDirective: Option[IndexingDirective] = indexingDirective,
      postTriggerInclude: Option[List[String]] = postTriggerInclude,
      preTriggerInclude: Option[List[String]] = preTriggerInclude,
      sessionToken: Option[String] = sessionToken
  ): ItemRequestOptions = new ItemRequestOptions(
    ifMatchEtag,
    ifNoneMatchEtag,
    indexingDirective,
    postTriggerInclude,
    preTriggerInclude,
    sessionToken
  )

  def withIfMatchEtag(value: Option[String]): ItemRequestOptions = this.copy(ifMatchEtag = value)
  def withIfNoneMatchEtag(value: Option[String]): ItemRequestOptions =
    this.copy(ifNoneMatchEtag = value)
  def withIndexingDirective(value: Option[IndexingDirective]): ItemRequestOptions =
    this.copy(indexingDirective = value)
  def withPostTriggerInclude(value: Option[List[String]]): ItemRequestOptions =
    this.copy(postTriggerInclude = value)
  def withPreTriggerInclude(value: Option[List[String]]): ItemRequestOptions =
    this.copy(preTriggerInclude = value)
  def withSessionToken(value: Option[String]): ItemRequestOptions = this.copy(sessionToken = value)

  override def toString: String =
    s"ItemRequestOptions($ifMatchEtag, $ifNoneMatchEtag, $indexingDirective, $postTriggerInclude, $preTriggerInclude, $sessionToken)"

  override def equals(o: Any): Boolean =
    o match {
      case x: ItemRequestOptions =>
        (this.ifMatchEtag == x.ifMatchEtag) &&
          (this.ifNoneMatchEtag == x.ifNoneMatchEtag) &&
          (this.indexingDirective == x.indexingDirective) &&
          (this.postTriggerInclude == x.postTriggerInclude) &&
          (this.preTriggerInclude == x.preTriggerInclude) &&
          (this.sessionToken == x.sessionToken)
      case _ => false
    }

  override def hashCode: Int =
    31 * (ifMatchEtag.## + 17) *
      31 * (ifNoneMatchEtag.## + 17) *
      31 * (indexingDirective.## + 17) *
      31 * (postTriggerInclude.## + 17) *
      31 * (preTriggerInclude.## + 17) *
      31 * (sessionToken.## + 17)

  private[cosmos4s] def build(): CosmosItemRequestOptions = {
    val options = new CosmosItemRequestOptions()
    ifMatchEtag.foreach(options.setIfMatchETag)
    ifNoneMatchEtag.foreach(options.setIfNoneMatchETag)
    indexingDirective.foreach(options.setIndexingDirective)
    postTriggerInclude.foreach(triggers => options.setPostTriggerInclude(triggers.asJava))
    preTriggerInclude.foreach(triggers => options.setPreTriggerInclude(triggers.asJava))
    sessionToken.foreach(options.setSessionToken)
    options
  }
}

object ItemRequestOptions {
  val default: ItemRequestOptions = new ItemRequestOptions(None, None, None, None, None, None)
}
