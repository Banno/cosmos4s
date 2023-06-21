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
import cats.syntax.all._
import com.azure.cosmos._
import com.azure.cosmos.implementation.NotFoundException
import com.azure.cosmos.models._
import com.banno.cosmos4s.types._
import com.fasterxml.jackson.databind.JsonNode
import fs2.{Chunk, Stream}
import io.circe.jackson._
import io.circe._
import scala.jdk.CollectionConverters._

trait IndexedCosmosContainer[F[_], K, I, V] {
  def query(
      partitionKey: K,
      query: String,
      overrides: QueryOptions => QueryOptions = identity
  ): Stream[F, V]
  def queryWithDiagnostics(
      partitionKey: K,
      query: String,
      overrides: QueryOptions => QueryOptions,
      handleDiagnostics: CosmosDiagnostics => F[Unit]
  ): Stream[F, V]
  def queryCustom[A: Decoder](
      partitionKey: K,
      query: String,
      overrides: QueryOptions => QueryOptions = identity
  ): Stream[F, A]
  def queryCustomWithDiagnostics[A: Decoder](
      partitionKey: K,
      query: String,
      overrides: QueryOptions => QueryOptions,
      handleDiagnostics: CosmosDiagnostics => F[Unit]
  ): Stream[F, A]

  def lookup(partitionKey: K, id: I): F[Option[V]]
  def insert(partitionKey: K, value: V): F[Option[V]]
  def replace(partitionKey: K, id: I, value: V): F[Option[V]]
  def upsert(partitionKey: K, value: V): F[Option[V]]
  def delete(partitionKey: K, id: I): F[Unit]

  def imapK[G[_]](fk: F ~> G, gk: G ~> F): IndexedCosmosContainer[G, K, I, V] =
    new IndexedCosmosContainer.IMapKIndexedCosmosContainer(this, fk, gk)
  def contramapPartitionKey[A](f: A => K): IndexedCosmosContainer[F, A, I, V] =
    new IndexedCosmosContainer.ContramapPartitionKey(this, f)
  def contramapId[A](f: A => I): IndexedCosmosContainer[F, K, A, V] =
    new IndexedCosmosContainer.ContramapId(this, f)
  def semiInvariantFlatMap[A](f: V => F[A])(g: A => V)(implicit
      F: Monad[F]
  ): IndexedCosmosContainer[F, K, I, A] =
    new IndexedCosmosContainer.SemiInvariantFlatMap(this, f, g)
  def imapValue[A](f: V => A)(g: A => V)(implicit
      F: Functor[F]
  ): IndexedCosmosContainer[F, K, I, A] =
    new IndexedCosmosContainer.ImapValue(this, f, g)
}

object IndexedCosmosContainer {

  def impl[F[_]: Async](
      container: CosmosAsyncContainer,
      createFeedOptions: Option[F[QueryOptions]] = None
  ): IndexedCosmosContainer[F, String, String, Json] =
    new BaseImpl[F](container, createFeedOptions)

  private class BaseImpl[F[_]: Async](
      container: CosmosAsyncContainer,
      createFeedOptions: Option[F[QueryOptions]] = None
  ) extends IndexedCosmosContainer[F, String, String, Json] {

    private def createFeedOptionsAlways: F[QueryOptions] =
      createFeedOptions.getOrElse(Sync[F].delay(QueryOptions.default))

    private val defaultDiagnoticsHandler: CosmosDiagnostics => F[Unit] =
      _ => Applicative[F].unit

    def query(
        partitionKey: String,
        query: String,
        overrides: QueryOptions => QueryOptions = identity
    ): Stream[F, Json] =
      queryCustomWithDiagnostics[Json](partitionKey, query, overrides, defaultDiagnoticsHandler)

    def queryWithDiagnostics(
        partitionKey: String,
        query: String,
        overrides: QueryOptions => QueryOptions,
        handleDiagnostics: CosmosDiagnostics => F[Unit]
    ): Stream[F, Json] =
      queryCustomWithDiagnostics[Json](partitionKey, query, overrides, handleDiagnostics)

    def queryCustom[A: Decoder](
        partitionKey: String,
        query: String,
        overrides: QueryOptions => QueryOptions = identity
    ): Stream[F, A] =
      queryCustomWithDiagnostics[A](partitionKey, query, overrides, defaultDiagnoticsHandler)

    def queryCustomWithDiagnostics[A: Decoder](
        partitionKey: String,
        query: String,
        overrides: QueryOptions => QueryOptions,
        handleDiagnostics: CosmosDiagnostics => F[Unit]
    ): Stream[F, A] =
      Stream
        .eval(createFeedOptionsAlways)
        .map(overrides)
        .flatMap { options =>
          ReactorCore.fluxToStream(
            Sync[F].delay(
              container
                .queryItems(
                  query,
                  options.build().setPartitionKey(new PartitionKey(partitionKey)),
                  classOf[JsonNode]
                )
                .byPage()
            )
          )
        }
        .flatMap { page =>
          val elements = page.getElements()
          val stream =
            if (elements == null) Stream.empty
            else
              Chunk
                .iterable(elements.asScala)
                .traverse(jacksonToCirce(_).as[A])
                .fold(Stream.raiseError[F], Stream.chunk)
          Stream.exec(handleDiagnostics(page.getCosmosDiagnostics())) ++ stream
        }

    def lookup(partitionKey: String, id: String): F[Option[Json]] =
      cats.data
        .OptionT(
          ReactorCore
            .monoToEffectOpt(
              Sync[F].delay(
                container.readItem(
                  id,
                  new PartitionKey(partitionKey),
                  new CosmosItemRequestOptions(),
                  classOf[JsonNode]
                )
              )
            )
            .recoverWith { case _: NotFoundException => Sync[F].pure(None) }
        )
        .subflatMap(response => Option(response.getItem()))
        .map(jacksonToCirce(_))
        .value

    def insert(partitionKey: String, value: Json): F[Option[Json]] =
      cats.data.OptionT
        .liftF(Sync[F].delay(new CosmosItemRequestOptions()))
        .flatMap(options =>
          cats.data.OptionT(
            ReactorCore.monoToEffectOpt(
              Sync[F].delay(
                container.createItem(circeToJackson(value), new PartitionKey(partitionKey), options)
              )
            )
          )
        )
        .subflatMap(response => Option(response.getItem()))
        .map(jacksonToCirce)
        .value

    def replace(partitionKey: String, id: String, value: Json): F[Option[Json]] =
      cats.data
        .OptionT(
          ReactorCore.monoToEffectOpt(
            Sync[F].delay(
              container.replaceItem(circeToJackson(value), id, new PartitionKey(partitionKey))
            )
          )
        )
        .subflatMap(response => Option(response.getItem()))
        .map(jacksonToCirce)
        .value

    def upsert(partitionKey: String, value: Json): F[Option[Json]] =
      cats.data
        .OptionT(
          ReactorCore.monoToEffectOpt(
            Sync[F].delay(
              container.upsertItem(circeToJackson(value))
            )
          )
        )
        .subflatMap(response => Option(response.getItem()))
        .map(jacksonToCirce)
        .value

    def delete(partitionKey: String, id: String): F[Unit] =
      ReactorCore
        .monoToEffect(
          Sync[F].delay(
            container.deleteItem(id, new PartitionKey(partitionKey))
          )
        )
        .void
  }

  private class IMapKIndexedCosmosContainer[F[_], G[_], K, I, V](
      base: IndexedCosmosContainer[F, K, I, V],
      fk: F ~> G,
      gk: G ~> F
  ) extends IndexedCosmosContainer[G, K, I, V] {
    def query(
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions = identity
    ): Stream[G, V] =
      base.query(partitionKey, query, overrides).translate(fk)
    def queryWithDiagnostics(
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions,
        handleDiagnostics: CosmosDiagnostics => G[Unit]
    ): Stream[G, V] =
      base
        .queryWithDiagnostics(partitionKey, query, overrides, d => gk(handleDiagnostics(d)))
        .translate(fk)
    def queryCustom[A: Decoder](
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions = identity
    ): Stream[G, A] =
      base.queryCustom[A](partitionKey, query, overrides).translate(fk)
    def queryCustomWithDiagnostics[A: Decoder](
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions,
        handleDiagnostics: CosmosDiagnostics => G[Unit]
    ): Stream[G, A] =
      base
        .queryCustomWithDiagnostics[A](
          partitionKey,
          query,
          overrides,
          d => gk(handleDiagnostics(d))
        )
        .translate(fk)
    def lookup(partitionKey: K, id: I): G[Option[V]] =
      fk(base.lookup(partitionKey, id))
    def insert(partitionKey: K, value: V): G[Option[V]] =
      fk(base.insert(partitionKey, value))
    def replace(partitionKey: K, id: I, value: V): G[Option[V]] =
      fk(base.replace(partitionKey, id, value))
    def upsert(partitionKey: K, value: V): G[Option[V]] =
      fk(base.upsert(partitionKey, value))
    def delete(partitionKey: K, id: I): G[Unit] =
      fk(base.delete(partitionKey, id))
  }

  private class ContramapPartitionKey[F[_], K, K2, I, V](
      base: IndexedCosmosContainer[F, K, I, V],
      contra: K2 => K
  ) extends IndexedCosmosContainer[F, K2, I, V] {
    def query(
        partitionKey: K2,
        query: String,
        overrides: QueryOptions => QueryOptions
    ): Stream[F, V] =
      base.query(contra(partitionKey), query, overrides)
    def queryWithDiagnostics(
        partitionKey: K2,
        query: String,
        overrides: QueryOptions => QueryOptions,
        handleDiagnostics: CosmosDiagnostics => F[Unit]
    ): Stream[F, V] =
      base.queryWithDiagnostics(contra(partitionKey), query, overrides, handleDiagnostics)
    def queryCustom[A: Decoder](
        partitionKey: K2,
        query: String,
        overrides: QueryOptions => QueryOptions
    ): Stream[F, A] =
      base.queryCustom(contra(partitionKey), query, overrides)
    def queryCustomWithDiagnostics[A: Decoder](
        partitionKey: K2,
        query: String,
        overrides: QueryOptions => QueryOptions,
        handleDiagnostics: CosmosDiagnostics => F[Unit]
    ): Stream[F, A] =
      base.queryCustomWithDiagnostics[A](contra(partitionKey), query, overrides, handleDiagnostics)
    def lookup(partitionKey: K2, id: I): F[Option[V]] =
      base.lookup(contra(partitionKey), id)
    def insert(partitionKey: K2, value: V): F[Option[V]] =
      base.insert(contra(partitionKey), value)
    def replace(partitionKey: K2, id: I, value: V): F[Option[V]] =
      base.replace(contra(partitionKey), id, value)
    def upsert(partitionKey: K2, value: V): F[Option[V]] =
      base.upsert(contra(partitionKey), value)
    def delete(partitionKey: K2, id: I): F[Unit] =
      base.delete(contra(partitionKey), id)
  }

  private class ContramapId[F[_], K, I, I2, V](
      base: IndexedCosmosContainer[F, K, I, V],
      contra: I2 => I
  ) extends IndexedCosmosContainer[F, K, I2, V] {
    def query(
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions
    ): Stream[F, V] =
      base.query(partitionKey, query, overrides)
    def queryWithDiagnostics(
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions,
        handleDiagnostics: CosmosDiagnostics => F[Unit]
    ): Stream[F, V] =
      base.queryWithDiagnostics(partitionKey, query, overrides, handleDiagnostics)
    def queryCustom[A: Decoder](
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions
    ): Stream[F, A] =
      base.queryCustom(partitionKey, query, overrides)
    def queryCustomWithDiagnostics[A: Decoder](
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions,
        handleDiagnostics: CosmosDiagnostics => F[Unit]
    ): Stream[F, A] =
      base.queryCustomWithDiagnostics[A](partitionKey, query, overrides, handleDiagnostics)
    def lookup(partitionKey: K, id: I2): F[Option[V]] =
      base.lookup(partitionKey, contra(id))
    def insert(partitionKey: K, value: V): F[Option[V]] =
      base.insert(partitionKey, value)
    def replace(partitionKey: K, id: I2, value: V): F[Option[V]] =
      base.replace(partitionKey, contra(id), value)
    def upsert(partitionKey: K, value: V): F[Option[V]] =
      base.upsert(partitionKey, value)
    def delete(partitionKey: K, id: I2): F[Unit] =
      base.delete(partitionKey, contra(id))
  }

  private class SemiInvariantFlatMap[F[_]: Monad, K, I, V, V2](
      base: IndexedCosmosContainer[F, K, I, V],
      f: V => F[V2],
      g: V2 => V
  ) extends IndexedCosmosContainer[F, K, I, V2] {
    def query(
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions
    ): Stream[F, V2] =
      base
        .query(partitionKey, query, overrides)
        .evalMapChunk(f)
    def queryWithDiagnostics(
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions,
        handleDiagnostics: CosmosDiagnostics => F[Unit]
    ): Stream[F, V2] =
      base
        .queryWithDiagnostics(partitionKey, query, overrides, handleDiagnostics)
        .evalMapChunk(f)
    def queryCustom[A: Decoder](
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions
    ): Stream[F, A] =
      base.queryCustom(partitionKey, query, overrides)
    def queryCustomWithDiagnostics[A: Decoder](
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions,
        handleDiagnostics: CosmosDiagnostics => F[Unit]
    ): Stream[F, A] =
      base.queryCustomWithDiagnostics[A](partitionKey, query, overrides, handleDiagnostics)
    def lookup(partitionKey: K, id: I): F[Option[V2]] =
      base.lookup(partitionKey, id).flatMap(_.traverse(f))
    def insert(partitionKey: K, value: V2): F[Option[V2]] =
      base.insert(partitionKey, g(value)).flatMap(_.traverse(f))
    def replace(partitionKey: K, id: I, value: V2): F[Option[V2]] =
      base.replace(partitionKey, id, g(value)).flatMap(_.traverse(f))
    def upsert(partitionKey: K, value: V2): F[Option[V2]] =
      base.upsert(partitionKey, g(value)).flatMap(_.traverse(f))
    def delete(partitionKey: K, id: I): F[Unit] =
      base.delete(partitionKey, id)
  }

  private class ImapValue[F[_]: Functor, K, I, V, V2](
      base: IndexedCosmosContainer[F, K, I, V],
      f: V => V2,
      g: V2 => V
  ) extends IndexedCosmosContainer[F, K, I, V2] {
    def query(
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions
    ): Stream[F, V2] =
      base
        .query(partitionKey, query, overrides)
        .map(f)
    def queryWithDiagnostics(
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions,
        handleDiagnostics: CosmosDiagnostics => F[Unit]
    ): Stream[F, V2] =
      base
        .queryWithDiagnostics(partitionKey, query, overrides, handleDiagnostics)
        .map(f)
    def queryCustom[A: Decoder](
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions
    ): Stream[F, A] =
      base.queryCustom(partitionKey, query, overrides)
    def queryCustomWithDiagnostics[A: Decoder](
        partitionKey: K,
        query: String,
        overrides: QueryOptions => QueryOptions,
        handleDiagnostics: CosmosDiagnostics => F[Unit]
    ): Stream[F, A] =
      base.queryCustomWithDiagnostics(partitionKey, query, overrides, handleDiagnostics)
    def lookup(partitionKey: K, id: I): F[Option[V2]] =
      base.lookup(partitionKey, id).map(_.map(f))
    def insert(partitionKey: K, value: V2): F[Option[V2]] =
      base.insert(partitionKey, g(value)).map(_.map(f))
    def replace(partitionKey: K, id: I, value: V2): F[Option[V2]] =
      base.replace(partitionKey, id, g(value)).map(_.map(f))
    def upsert(partitionKey: K, value: V2): F[Option[V2]] =
      base.upsert(partitionKey, g(value)).map(_.map(f))
    def delete(partitionKey: K, id: I): F[Unit] =
      base.delete(partitionKey, id)
  }

  implicit def partitionKey[F[_], I, V]: Contravariant[IndexedCosmosContainer[F, *, I, V]] =
    new Contravariant[IndexedCosmosContainer[F, *, I, V]] {
      def contramap[A, B](fa: IndexedCosmosContainer[F, A, I, V])(
          f: B => A
      ): IndexedCosmosContainer[F, B, I, V] =
        fa.contramapPartitionKey(f)
    }

  implicit def id[F[_], K, V]: Contravariant[IndexedCosmosContainer[F, K, *, V]] =
    new Contravariant[IndexedCosmosContainer[F, K, *, V]] {
      def contramap[A, B](fa: IndexedCosmosContainer[F, K, A, V])(
          f: B => A
      ): IndexedCosmosContainer[F, K, B, V] =
        fa.contramapId(f)
    }
}
