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

package com.banno

import cats.Applicative
import cats.effect.Async
import cats.syntax.all._
import com.banno.cosmos4s.types._
import com.azure.cosmos.CosmosDiagnostics
import io.circe._
import fs2._

package object cosmos4s {
  type BaseCosmosContainer[F[_]] =
    CosmosContainer[F, String, String, Either[FeedResponse, *], Json, ItemResponse[*], Json]

  implicit class BaseContainerOps[F[_]](private val base: BaseCosmosContainer[F]) extends AnyVal {
    def toIndexedContainer(createFeedOptions: Option[F[QueryOptions]] = None)(implicit
        ev: Async[F]
    ): IndexedCosmosContainer[F, String, String, Json] =
      IndexedCosmosContainer.impl(base, createFeedOptions)
    def toRawContainer(createFeedOptions: Option[F[QueryOptions]] = None)(implicit
        ev: Async[F]
    ): RawCosmosContainer[F, Json] =
      RawCosmosContainer.impl(base, createFeedOptions)
  }

  implicit class ResultStream[F[_], A](private val stream: Stream[F, Either[FeedResponse, A]])
      extends AnyVal {
    def handleResultMeta(f: FeedResponse => F[Unit])(implicit ev: Applicative[F]): Stream[F, A] =
      stream
        .evalMapChunk {
          case Left(response) => f(response).as[Option[A]](None)
          case Right(a) => a.some.pure
        }
        .collect { case Some(a) => a }

    def handleDiagnostics(f: CosmosDiagnostics => F[Unit])(implicit
        ev: Applicative[F]
    ): Stream[F, A] =
      stream.handleResultMeta(response => f(response.cosmosDiagnostics))
  }
}
