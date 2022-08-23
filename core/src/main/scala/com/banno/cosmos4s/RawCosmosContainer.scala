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

import cats._
import cats.effect._
import com.azure.cosmos._
import com.azure.cosmos.models._
import com.banno.cosmos4s.types._
import com.fasterxml.jackson.databind.JsonNode
import fs2.{Chunk, Stream}
import io.circe.jackson._
import io.circe._

trait RawCosmosContainer[F[_], V] {
  def queryRaw(
      query: String,
      parameters: Map[String, Any] = Map.empty,
      overrides: QueryOptions => QueryOptions = identity
  ): Stream[F, V]
  def queryCustomRaw[A: Decoder](
      query: String,
      parameters: Map[String, Any] = Map.empty,
      overrides: QueryOptions => QueryOptions = identity
  ): Stream[F, A]

  def map[A](f: V => A): RawCosmosContainer[F, A] =
    new RawCosmosContainer.MapValueRawCosmosContainter(this, f)
  def evalMap[A](f: V => F[A]): RawCosmosContainer[F, A] =
    new RawCosmosContainer.EvalMapRawCosmosContainer(this, f)
  def mapK[G[_]](fk: F ~> G): RawCosmosContainer[G, V] =
    new RawCosmosContainer.MapKRawCosmosContainer(this, fk)
}

object RawCosmosContainer {
  def impl[F[_]: Async](
      container: CosmosAsyncContainer,
      createQueryOptions: Option[F[QueryOptions]] = None
  ): RawCosmosContainer[F, Json] =
    new BaseImpl[F](container, createQueryOptions)

  private class BaseImpl[F[_]: Async](
      container: CosmosAsyncContainer,
      createQueryOptions: Option[F[QueryOptions]] = None
  ) extends RawCosmosContainer[F, Json] {

    def createQueryOptionsAlways: F[QueryOptions] =
      createQueryOptions.getOrElse(Sync[F].delay(QueryOptions.default))

    def queryRaw(
        query: String,
        parameters: Map[String, Any],
        overrides: QueryOptions => QueryOptions = identity
    ): Stream[F, Json] =
      queryCustomRaw[Json](query, parameters, overrides)

    import collection.JavaConverters._

    def queryCustomRaw[A: Decoder](
        query: String,
        parameters: Map[String, Any],
        overrides: QueryOptions => QueryOptions = identity
    ): Stream[F, A] =
      Stream
        .eval(createQueryOptionsAlways)
        .map(overrides)
        .flatMap { options =>
          val sqlParams = parameters
            .map {
              case (key, value) =>
                new SqlParameter(key, value)
            }
            .toList
            .asJava
          val querySpec = new SqlQuerySpec(query, sqlParams)
          ReactorCore.fluxToStream(
            Sync[F].delay(
              container
                .queryItems(querySpec, options.build(), classOf[JsonNode])
                .byPage()
            )
          )
        }
        .flatMap { page =>
          val elements = page.getElements()
          if (elements == null) Stream.empty
          else
            Chunk
              .iterable(elements.asScala)
              .traverse(jacksonToCirce(_).as[A])
              .fold(Stream.raiseError[F] _, Stream.chunk(_))
        }
  }

  private class MapKRawCosmosContainer[F[_], G[_], V](
      base: RawCosmosContainer[F, V],
      fk: F ~> G
  ) extends RawCosmosContainer[G, V] {
    def queryRaw(
        query: String,
        parameters: Map[String, Any],
        overrides: QueryOptions => QueryOptions = identity
    ): Stream[G, V] =
      base.queryRaw(query, parameters, overrides).translate(fk)
    def queryCustomRaw[A: Decoder](
        query: String,
        parameters: Map[String, Any],
        overrides: QueryOptions => QueryOptions = identity
    ): Stream[G, A] =
      base.queryCustomRaw(query, parameters, overrides).translate(fk)
  }

  private class MapValueRawCosmosContainter[F[_], V, A](
      base: RawCosmosContainer[F, V],
      f: V => A
  ) extends RawCosmosContainer[F, A] {
    def queryRaw(
        query: String,
        parameters: Map[String, Any],
        overrides: QueryOptions => QueryOptions = identity
    ): Stream[F, A] =
      base.queryRaw(query, parameters, overrides).map(f)

    def queryCustomRaw[B: Decoder](
        query: String,
        parameters: Map[String, Any],
        overrides: QueryOptions => QueryOptions
    ): Stream[F, B] =
      base.queryCustomRaw(query, parameters, overrides)
  }

  private class EvalMapRawCosmosContainer[F[_], V, A](
      base: RawCosmosContainer[F, V],
      f: V => F[A]
  ) extends RawCosmosContainer[F, A] {
    def queryRaw(
        query: String,
        parameters: Map[String, Any],
        overrides: QueryOptions => QueryOptions = identity
    ): Stream[F, A] =
      base.queryRaw(query, parameters, overrides).evalMap(f)

    def queryCustomRaw[B: Decoder](
        query: String,
        parameters: Map[String, Any],
        overrides: QueryOptions => QueryOptions
    ): Stream[F, B] =
      base.queryCustomRaw(query, parameters, overrides)
  }

  implicit def functor[F[_]]: Functor[RawCosmosContainer[F, *]] =
    new Functor[RawCosmosContainer[F, *]] {
      def map[A, B](fa: RawCosmosContainer[F, A])(f: A => B): RawCosmosContainer[F, B] = fa.map(f)
    }

}
