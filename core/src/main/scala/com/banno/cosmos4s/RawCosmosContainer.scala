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
import com.banno.cosmos4s.types._
import fs2.Stream
import io.circe._
import scala.jdk.CollectionConverters._

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
      container: BaseCosmosContainer[F],
      createQueryOptions: Option[F[QueryOptions]] = None
  ): RawCosmosContainer[F, Json] =
    new BaseImpl[F](container, createQueryOptions)

  private class BaseImpl[F[_]: Async](
      container: BaseCosmosContainer[F],
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

    def queryCustomRaw[A: Decoder](
        query: String,
        parameters: Map[String, Any],
        overrides: QueryOptions => QueryOptions = identity
    ): Stream[F, A] =
      Stream
        .eval(createQueryOptionsAlways)
        .map(overrides)
        .flatMap { options =>
          container.query(query, parameters, options)
        }
        .collect { case Right(json) => json }
        .evalMapChunk(json => MonadThrow[F].fromEither(json.as[A]))
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
