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

import _root_.io.circe._
import cats._
import cats.implicits._
import cats.effect._
import _root_.fs2._

import _root_.io.circe.jackson._
import com.fasterxml.jackson.databind.JsonNode

import com.azure.cosmos._
import com.azure.cosmos.models._

trait IndexedCosmosContainer[F[_], K, I, V] {
  def query(
      partitionKey: K,
      query: String,
      overrides: FeedOptions => FeedOptions = identity): Stream[F, V]
  def queryCustom[A: Decoder](
      partitionKey: K,
      query: String,
      overrides: FeedOptions => FeedOptions = identity): Stream[F, A]
  def lookup(partitionKey: K, id: I): F[Option[V]]
  def insert(partitionKey: K, value: V): F[Option[V]]
  def replace(partitionKey: K, id: I, value: V): F[Option[V]]
  def upsert(partitionKey: K, value: V): F[Option[V]]
  def delete(partitionKey: K, id: I): F[Unit]

  def mapK[G[_]](fk: F ~> G): IndexedCosmosContainer[G, K, I, V] =
    new IndexedCosmosContainer.MapKIndexedCosmosContainer(this, fk)
  def contramapPartitionKey[A](f: A => K): IndexedCosmosContainer[F, A, I, V] =
    new IndexedCosmosContainer.ContramapPartitionKey(this, f)
  def contramapId[A](f: A => I): IndexedCosmosContainer[F, K, A, V] =
    new IndexedCosmosContainer.ContramapId(this, f)
  def semiInvariantFlatMap[A](f: V => F[A])(g: A => V)(implicit
      F: Monad[F]): IndexedCosmosContainer[F, K, I, A] =
    new IndexedCosmosContainer.SemiInvariantFlatMap(this, f, g)
  def imapValue[A](f: V => A)(g: A => V)(implicit
      F: Functor[F]): IndexedCosmosContainer[F, K, I, A] =
    new IndexedCosmosContainer.ImapValue(this, f, g)
}

object IndexedCosmosContainer {

  def impl[F[_]: ConcurrentEffect: ContextShift](
      container: CosmosAsyncContainer,
      createFeedOptions: Option[F[FeedOptions]] = None)
      : IndexedCosmosContainer[F, String, String, Json] =
    new BaseImpl[F](container, createFeedOptions)

  private class BaseImpl[F[_]: ConcurrentEffect: ContextShift](
      container: CosmosAsyncContainer,
      createFeedOptions: Option[F[FeedOptions]] = None)
      extends IndexedCosmosContainer[F, String, String, Json] {

    def createFeedOptionsAlways = createFeedOptions.getOrElse(Sync[F].delay(new FeedOptions()))
    import scala.collection.JavaConverters._
    def query(
        partitionKey: String,
        query: String,
        overrides: FeedOptions => FeedOptions = identity): Stream[F, Json] =
      queryCustom[Json](partitionKey, query, overrides)
    def queryCustom[A: Decoder](
        partitionKey: String,
        query: String,
        overrides: FeedOptions => FeedOptions = identity): Stream[F, A] =
      Stream
        .eval(createFeedOptionsAlways)
        .map(overrides)
        .flatMap { options =>
          ReactorCore.fluxToStream(
            Sync[F].delay(
              container
                .queryItems(
                  query,
                  options.setPartitionKey(new PartitionKey(partitionKey)),
                  classOf[JsonNode])
                .byPage()
            )
          )
        }
        .flatMap(page => Stream.fromIterator(page.getElements().iterator().asScala))
        .map(jacksonToCirce)
        .evalMap(_.as[A].liftTo[F])
    def lookup(partitionKey: String, id: String): F[Option[Json]] =
      cats.data
        .OptionT(
          ReactorCore.monoToEffectOpt(
            Sync[F].delay(
              container.readItem(
                id,
                new PartitionKey(partitionKey),
                new CosmosItemRequestOptions(),
                classOf[JsonNode])
            ))
        )
        .subflatMap(response => Option(response.getItem()))
        .map(jacksonToCirce)
        .value
    def insert(partitionKey: String, value: Json): F[Option[Json]] =
      cats.data.OptionT
        .liftF(Sync[F].delay(new CosmosItemRequestOptions()))
        .flatMap(options =>
          cats.data.OptionT(ReactorCore.monoToEffectOpt(Sync[F].delay(
            container.createItem(circeToJackson(value), new PartitionKey(partitionKey), options)))))
        .subflatMap(response => Option(response.getItem()))
        .map(jacksonToCirce)
        .value
    def replace(partitionKey: String, id: String, value: Json): F[Option[Json]] =
      cats.data
        .OptionT(
          ReactorCore.monoToEffectOpt(
            Sync[F].delay(
              container.replaceItem(circeToJackson(value), id, new PartitionKey(partitionKey))
            ))
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
            ))
        )
        .subflatMap(response => Option(response.getItem()))
        .map(jacksonToCirce)
        .value

    def delete(partitionKey: String, id: String): F[Unit] =
      ReactorCore
        .monoToEffect(
          Sync[F].delay(
            container.deleteItem(id, new PartitionKey(partitionKey))
          ))
        .void
  }

  private class MapKIndexedCosmosContainer[F[_], G[_], K, I, V](
      base: IndexedCosmosContainer[F, K, I, V],
      fk: F ~> G
  ) extends IndexedCosmosContainer[G, K, I, V] {
    def query(
        partitionKey: K,
        query: String,
        overrides: FeedOptions => FeedOptions = identity): Stream[G, V] =
      base.query(partitionKey, query, overrides).translate(fk)
    def queryCustom[A: Decoder](
        partitionKey: K,
        query: String,
        overrides: FeedOptions => FeedOptions = identity): Stream[G, A] =
      base.queryCustom[A](partitionKey, query, overrides).translate(fk)
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
        overrides: FeedOptions => FeedOptions): Stream[F, V] =
      base.query(contra(partitionKey), query, overrides)
    def queryCustom[A: Decoder](
        partitionKey: K2,
        query: String,
        overrides: FeedOptions => FeedOptions): Stream[F, A] =
      base.queryCustom(contra(partitionKey), query, overrides)
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
    def query(partitionKey: K, query: String, overrides: FeedOptions => FeedOptions): Stream[F, V] =
      base.query(partitionKey, query, overrides)
    def queryCustom[A: Decoder](
        partitionKey: K,
        query: String,
        overrides: FeedOptions => FeedOptions): Stream[F, A] =
      base.queryCustom(partitionKey, query, overrides)
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
        overrides: FeedOptions => FeedOptions): Stream[F, V2] =
      base
        .query(partitionKey, query, overrides)
        .evalMap(f)
    def queryCustom[A: Decoder](
        partitionKey: K,
        query: String,
        overrides: FeedOptions => FeedOptions): Stream[F, A] =
      base.queryCustom(partitionKey, query, overrides)
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
        overrides: FeedOptions => FeedOptions): Stream[F, V2] =
      base
        .query(partitionKey, query, overrides)
        .map(f)
    def queryCustom[A: Decoder](
        partitionKey: K,
        query: String,
        overrides: FeedOptions => FeedOptions): Stream[F, A] =
      base.queryCustom(partitionKey, query, overrides)
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

  implicit def partitionKey[F[_], I, V] =
    new Contravariant[IndexedCosmosContainer[F, *, I, V]] {
      def contramap[A, B](fa: IndexedCosmosContainer[F, A, I, V])(
          f: B => A): IndexedCosmosContainer[F, B, I, V] =
        fa.contramapPartitionKey(f)
    }

  implicit def id[F[_], K, V] =
    new Contravariant[IndexedCosmosContainer[F, K, *, V]] {
      def contramap[A, B](fa: IndexedCosmosContainer[F, K, A, V])(
          f: B => A): IndexedCosmosContainer[F, K, B, V] =
        fa.contramapId(f)
    }
}
