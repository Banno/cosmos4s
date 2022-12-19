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

import cats.{~>, Functor}
import cats.implicits._
import com.banno.cosmos4s.types._
import fs2.Stream
import cats.Monad
import cats.Traverse
import cats.Applicative

trait CosmosContainer[F[_], Key, Id, StreamResult[_], QueryValue, SingleResult[_], SingleValue] {
  def query(
      query: String,
      parameters: Map[String, Any] = Map.empty,
      options: QueryOptions = QueryOptions.default
  ): Stream[F, StreamResult[QueryValue]]
  def lookup(
      partitionKey: Key,
      id: Id,
      options: ItemRequestOptions = ItemRequestOptions.default
  ): F[Option[SingleResult[SingleValue]]]
  def insert(
      partitionKey: Key,
      value: SingleValue,
      options: ItemRequestOptions = ItemRequestOptions.default
  ): F[Option[SingleResult[SingleValue]]]
  def replace(
      partitionKey: Key,
      id: Id,
      value: SingleValue,
      options: ItemRequestOptions = ItemRequestOptions.default
  ): F[Option[SingleResult[SingleValue]]]
  def upsert(
      value: SingleValue,
      options: ItemRequestOptions = ItemRequestOptions.default
  ): F[Option[SingleResult[SingleValue]]]
  def delete(
      partitionKey: Key,
      id: Id,
      options: ItemRequestOptions = ItemRequestOptions.default
  ): F[SingleResult[Unit]]

  def imapK[G[_]](
      fk: F ~> G
  ): CosmosContainer[G, Key, Id, StreamResult, QueryValue, SingleResult, SingleValue] =
    new CosmosContainer.IMapKCosmosContainer(this, fk)
  def contramapPartitionKey[A](
      f: A => Key
  ): CosmosContainer[F, A, Id, StreamResult, QueryValue, SingleResult, SingleValue] =
    new CosmosContainer.ContramapPartitionKey(this, f)
  def contramapId[A](
      f: A => Id
  ): CosmosContainer[F, Key, A, StreamResult, QueryValue, SingleResult, SingleValue] =
    new CosmosContainer.ContramapId(this, f)

  def semiInvariantFlatMapSingleResult[A](f: SingleValue => F[A], g: A => SingleValue)(implicit
      ev1: Monad[F],
      ev2: Traverse[SingleResult]
  ): CosmosContainer[F, Key, Id, StreamResult, QueryValue, SingleResult, A] =
    new CosmosContainer.SemiInvariantFlatMapSingleResult(this, f, g)

  def semiInvariantFlatMapStreamResult[A](f: QueryValue => F[A])(implicit
      ev1: Applicative[F],
      ev2: Traverse[StreamResult]
  ): CosmosContainer[F, Key, Id, StreamResult, A, SingleResult, SingleValue] =
    new CosmosContainer.SemiInvariantFlatMapStreamResult(this, f)

  def semiInvariantFlatMap[A](f: SingleValue => F[A], g: A => SingleValue, h: QueryValue => F[A])(
      implicit
      ev1: Monad[F],
      ev2: Traverse[SingleResult],
      ev3: Traverse[StreamResult]
  ): CosmosContainer[F, Key, Id, StreamResult, A, SingleResult, A] =
    new CosmosContainer.SemiInvariantFlatMapStreamResult(
      new CosmosContainer.SemiInvariantFlatMapSingleResult(this, f, g),
      h
    )

  def dropSingleResultContext[A](f: SingleResult[SingleValue] => A, g: A => SingleValue)(implicit
      ev: Functor[F]
  ): CosmosContainer[F, Key, Id, StreamResult, QueryValue, cats.Id, A] =
    new CosmosContainer.SingleResultMap(this, f, g)

  def dropStreamResultContext[A](
      f: StreamResult[QueryValue] => A
  ): CosmosContainer[F, Key, Id, cats.Id, A, SingleResult, SingleValue] =
    new CosmosContainer.StreamResultMap(this, f)

  def dropResultContext[A, B](
      f: SingleResult[SingleValue] => A,
      g: A => SingleValue,
      h: StreamResult[QueryValue] => B
  )(implicit ev: Functor[F]): CosmosContainer[F, Key, Id, cats.Id, B, cats.Id, A] =
    new CosmosContainer.SingleResultMap(new CosmosContainer.StreamResultMap(this, h), f, g)
}

object CosmosContainer {

  private class IMapKCosmosContainer[F[_], G[_], Key, Id, StreamResult[_], QueryValue, SingleResult[
      _
  ], SingleValue](
      base: CosmosContainer[F, Key, Id, StreamResult, QueryValue, SingleResult, SingleValue],
      fk: F ~> G
  ) extends CosmosContainer[G, Key, Id, StreamResult, QueryValue, SingleResult, SingleValue] {
    def query(
        query: String,
        parameters: Map[String, Any],
        options: QueryOptions = QueryOptions.default
    ): Stream[G, StreamResult[QueryValue]] =
      base.query(query, parameters, options).translate(fk)
    def lookup(
        partitionKey: Key,
        id: Id,
        options: ItemRequestOptions
    ): G[Option[SingleResult[SingleValue]]] =
      fk(base.lookup(partitionKey, id, options))
    def insert(
        partitionKey: Key,
        value: SingleValue,
        options: ItemRequestOptions
    ): G[Option[SingleResult[SingleValue]]] =
      fk(base.insert(partitionKey, value, options))
    def replace(
        partitionKey: Key,
        id: Id,
        value: SingleValue,
        options: ItemRequestOptions
    ): G[Option[SingleResult[SingleValue]]] =
      fk(base.replace(partitionKey, id, value, options))
    def upsert(
        value: SingleValue,
        options: ItemRequestOptions
    ): G[Option[SingleResult[SingleValue]]] =
      fk(base.upsert(value, options))
    def delete(
        partitionKey: Key,
        id: Id,
        options: ItemRequestOptions
    ): G[SingleResult[Unit]] =
      fk(base.delete(partitionKey, id, options))
  }

  private class ContramapPartitionKey[F[_], Key, Key2, Id, StreamResult[
      _
  ], QueryValue, SingleResult[_], SingleValue](
      base: CosmosContainer[F, Key, Id, StreamResult, QueryValue, SingleResult, SingleValue],
      contra: Key2 => Key
  ) extends CosmosContainer[F, Key2, Id, StreamResult, QueryValue, SingleResult, SingleValue] {
    def query(
        query: String,
        parameters: Map[String, Any],
        options: QueryOptions = QueryOptions.default
    ): Stream[F, StreamResult[QueryValue]] =
      base.query(query, parameters, options)
    def lookup(
        partitionKey: Key2,
        id: Id,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.lookup(contra(partitionKey), id, options)
    def insert(
        partitionKey: Key2,
        value: SingleValue,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.insert(contra(partitionKey), value, options)
    def replace(
        partitionKey: Key2,
        id: Id,
        value: SingleValue,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.replace(contra(partitionKey), id, value, options)
    def upsert(
        value: SingleValue,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.upsert(value, options)
    def delete(
        partitionKey: Key2,
        id: Id,
        options: ItemRequestOptions
    ): F[SingleResult[Unit]] =
      base.delete(contra(partitionKey), id, options)
  }

  private class ContramapId[F[_], Key, Id, Id2, StreamResult[_], QueryValue, SingleResult[
      _
  ], SingleValue](
      base: CosmosContainer[F, Key, Id, StreamResult, QueryValue, SingleResult, SingleValue],
      contra: Id2 => Id
  ) extends CosmosContainer[F, Key, Id2, StreamResult, QueryValue, SingleResult, SingleValue] {
    def query(
        query: String,
        parameters: Map[String, Any],
        options: QueryOptions
    ): Stream[F, StreamResult[QueryValue]] =
      base.query(query, parameters, options)
    def lookup(
        partitionKey: Key,
        id: Id2,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.lookup(partitionKey, contra(id), options)
    def insert(
        partitionKey: Key,
        value: SingleValue,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.insert(partitionKey, value, options)
    def replace(
        partitionKey: Key,
        id: Id2,
        value: SingleValue,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.replace(partitionKey, contra(id), value, options)
    def upsert(
        value: SingleValue,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.upsert(value, options)
    def delete(
        partitionKey: Key,
        id: Id2,
        options: ItemRequestOptions
    ): F[SingleResult[Unit]] =
      base.delete(partitionKey, contra(id), options)
  }

  private class SemiInvariantFlatMapSingleResult[F[_]: Monad, Key, Id, StreamResult[
      _
  ], QueryValue, SingleResult[
      _
  ]: Traverse, SingleValue, A](
      base: CosmosContainer[F, Key, Id, StreamResult, QueryValue, SingleResult, SingleValue],
      f: SingleValue => F[A],
      g: A => SingleValue
  ) extends CosmosContainer[F, Key, Id, StreamResult, QueryValue, SingleResult, A] {
    def query(
        query: String,
        parameters: Map[String, Any],
        options: QueryOptions
    ): Stream[F, StreamResult[QueryValue]] =
      base.query(query, parameters, options)
    def lookup(
        partitionKey: Key,
        id: Id,
        options: ItemRequestOptions
    ): F[Option[SingleResult[A]]] =
      base.lookup(partitionKey, id, options).flatMap(_.traverse(_.traverse(f)))
    def insert(
        partitionKey: Key,
        value: A,
        options: ItemRequestOptions
    ): F[Option[SingleResult[A]]] =
      base.insert(partitionKey, g(value), options).flatMap(_.traverse(_.traverse(f)))
    def replace(
        partitionKey: Key,
        id: Id,
        value: A,
        options: ItemRequestOptions
    ): F[Option[SingleResult[A]]] =
      base.replace(partitionKey, id, g(value), options).flatMap(_.traverse(_.traverse(f)))

    def upsert(
        value: A,
        options: ItemRequestOptions
    ): F[Option[SingleResult[A]]] =
      base.upsert(g(value), options).flatMap(_.traverse(_.traverse(f)))
    def delete(
        partitionKey: Key,
        id: Id,
        options: ItemRequestOptions
    ): F[SingleResult[Unit]] =
      base.delete(partitionKey, id, options)
  }

  private class SemiInvariantFlatMapStreamResult[F[_]: Applicative, Key, Id, StreamResult[
      _
  ]: Traverse, QueryValue, SingleResult[
      _
  ], SingleValue, A](
      base: CosmosContainer[F, Key, Id, StreamResult, QueryValue, SingleResult, SingleValue],
      f: QueryValue => F[A]
  ) extends CosmosContainer[F, Key, Id, StreamResult, A, SingleResult, SingleValue] {
    def query(
        query: String,
        parameters: Map[String, Any],
        options: QueryOptions
    ): Stream[F, StreamResult[A]] =
      base.query(query, parameters, options).evalMap(_.traverse(f))
    def lookup(
        partitionKey: Key,
        id: Id,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.lookup(partitionKey, id, options)
    def insert(
        partitionKey: Key,
        value: SingleValue,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.insert(partitionKey, value, options)
    def replace(
        partitionKey: Key,
        id: Id,
        value: SingleValue,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.replace(partitionKey, id, value, options)

    def upsert(
        value: SingleValue,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.upsert(value, options)
    def delete(
        partitionKey: Key,
        id: Id,
        options: ItemRequestOptions
    ): F[SingleResult[Unit]] =
      base.delete(partitionKey, id, options)
  }

  private class SingleResultMap[F[_]: Functor, Key, Id, StreamResult[_], QueryValue, SingleResult[
      _
  ], SingleValue, A](
      base: CosmosContainer[F, Key, Id, StreamResult, QueryValue, SingleResult, SingleValue],
      f: SingleResult[SingleValue] => A,
      g: A => SingleValue
  ) extends CosmosContainer[F, Key, Id, StreamResult, QueryValue, cats.Id, A] {
    def query(
        query: String,
        parameters: Map[String, Any],
        options: QueryOptions
    ): Stream[F, StreamResult[QueryValue]] =
      base.query(query, parameters, options)
    def lookup(
        partitionKey: Key,
        id: Id,
        options: ItemRequestOptions
    ): F[Option[A]] =
      base.lookup(partitionKey, id, options).map(_.map(f))
    def insert(
        partitionKey: Key,
        value: A,
        options: ItemRequestOptions
    ): F[Option[A]] =
      base.insert(partitionKey, g(value), options).map(_.map(f))
    def replace(
        partitionKey: Key,
        id: Id,
        value: A,
        options: ItemRequestOptions
    ): F[Option[A]] =
      base.replace(partitionKey, id, g(value), options).map(_.map(f))

    def upsert(
        value: A,
        options: ItemRequestOptions
    ): F[Option[A]] =
      base.upsert(g(value), options).map(_.map(f))
    def delete(
        partitionKey: Key,
        id: Id,
        options: ItemRequestOptions
    ): F[Unit] =
      base.delete(partitionKey, id, options).as(())
  }

  private class StreamResultMap[F[_], Key, Id, StreamResult[_], QueryValue, SingleResult[
      _
  ], SingleValue, A](
      base: CosmosContainer[F, Key, Id, StreamResult, QueryValue, SingleResult, SingleValue],
      f: StreamResult[QueryValue] => A
  ) extends CosmosContainer[F, Key, Id, cats.Id, A, SingleResult, SingleValue] {
    def query(
        query: String,
        parameters: Map[String, Any],
        options: QueryOptions
    ): Stream[F, A] =
      base.query(query, parameters, options).map(f)
    def lookup(
        partitionKey: Key,
        id: Id,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.lookup(partitionKey, id, options)
    def insert(
        partitionKey: Key,
        value: SingleValue,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.insert(partitionKey, value, options)
    def replace(
        partitionKey: Key,
        id: Id,
        value: SingleValue,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.replace(partitionKey, id, value, options)

    def upsert(
        value: SingleValue,
        options: ItemRequestOptions
    ): F[Option[SingleResult[SingleValue]]] =
      base.upsert(value, options)
    def delete(
        partitionKey: Key,
        id: Id,
        options: ItemRequestOptions
    ): F[SingleResult[Unit]] =
      base.delete(partitionKey, id, options)
  }
}
