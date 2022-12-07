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

package com.banno.cosmos4s

import cats.data.OptionT
import cats.effect._
import cats.syntax.all._
import com.azure.cosmos.CosmosAsyncContainer
import com.azure.cosmos.models.{CosmosItemRequestOptions, PartitionKey, SqlParameter, SqlQuerySpec}
import com.banno.cosmos4s.types._
import com.fasterxml.jackson.databind.JsonNode
import fs2.Stream
import scala.jdk.CollectionConverters._
import io.circe.jackson._
import io.circe._
import com.azure.cosmos.implementation.NotFoundException

object BaseCosmosContainer {

  val convertJson: Option[JsonNode] => Json = _.fold(Json.Null)(jacksonToCirce)

  def impl[F[_]: Async](
      container: CosmosAsyncContainer
  ): BaseCosmosContainer[F] =
    new CosmosContainer[F, String, String, Either[FeedResponse, *], Json, ItemResponse[*], Json] {

      def query(
          query: String,
          parameters: Map[String, Any],
          options: QueryOptions
      ): Stream[F, Either[FeedResponse, Json]] = {
        val sqlParams = parameters
          .map {
            case (key, value) =>
              new SqlParameter(key, value)
          }
          .toList
          .asJava
        val querySpec = new SqlQuerySpec(query, sqlParams)
        ReactorCore
          .fluxToStream(
            Sync[F].delay(
              container
                .queryItems(querySpec, options.build(), classOf[JsonNode])
                .byPage()
            )
          )
          .flatMap { page =>
            val elements = page.getElements()
            val itemStream =
              if (elements == null) Stream.empty
              else
                Stream
                  .iterable(elements.asScala)
                  .map(jacksonToCirce(_).asRight[FeedResponse])
            Stream.emit(FeedResponse.fromCosmosResponse(page).asLeft[Json]) ++ itemStream
          }
      }

      def delete(
          partitionKey: String,
          id: String,
          options: CosmosItemRequestOptions
      ): F[ItemResponse[Unit]] =
        ReactorCore
          .monoToEffect(
            Sync[F].delay(
              container.deleteItem(id, new PartitionKey(partitionKey), options)
            )
          )
          .map(ItemResponse.fromCosmosResponse(_ => ()))

      def insert(
          partitionKey: String,
          value: Json,
          options: CosmosItemRequestOptions
      ): F[Option[ItemResponse[Json]]] =
        OptionT(
          ReactorCore.monoToEffectOpt(
            Sync[F].delay(
              container.createItem(circeToJackson(value), new PartitionKey(partitionKey), options)
            )
          )
        ).map(ItemResponse.fromCosmosResponse(convertJson)).value

      def lookup(
          partitionKey: String,
          id: String,
          options: CosmosItemRequestOptions
      ): F[Option[ItemResponse[Json]]] =
        OptionT(
          ReactorCore
            .monoToEffectOpt(
              Sync[F].delay(
                container.readItem(
                  id,
                  new PartitionKey(partitionKey),
                  options,
                  classOf[JsonNode]
                )
              )
            )
            .recoverWith { case _: NotFoundException => Sync[F].pure(None) }
        )
          .map(ItemResponse.fromCosmosResponse(convertJson))
          .value

      def upsert(value: Json, options: CosmosItemRequestOptions): F[Option[ItemResponse[Json]]] =
        OptionT(
          ReactorCore.monoToEffectOpt(
            Sync[F].delay(
              container.upsertItem(circeToJackson(value), options)
            )
          )
        )
          .map(ItemResponse.fromCosmosResponse(convertJson))
          .value

      def replace(
          partitionKey: String,
          id: String,
          value: Json,
          options: CosmosItemRequestOptions
      ): F[Option[ItemResponse[Json]]] =
        OptionT(
          ReactorCore.monoToEffectOpt(
            Sync[F].delay(
              container.replaceItem(
                circeToJackson(value),
                id,
                new PartitionKey(partitionKey),
                options
              )
            )
          )
        )
          .map(ItemResponse.fromCosmosResponse(convertJson))
          .value

    }
}
