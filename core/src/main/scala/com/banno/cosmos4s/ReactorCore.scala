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

import cats.effect._
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2.Stream
import fs2.interop.flow._
import reactor.core.publisher._
import reactor.adapter.JdkFlowAdapter.publisherToFlowPublisher

object ReactorCore {
  def monoToEffectOpt[F[_]: Async, A](m: F[Mono[A]]): F[Option[A]] =
    Stream
      .eval(m)
      .flatMap(m => fromPublisher(publisherToFlowPublisher(m), 1))
      .compile
      .last
      .guarantee(Spawn[F].cede)

  def monoToEffect[F[_]: Async, A](m: F[Mono[A]]): F[A] =
    monoToEffectOpt(m).flatMap(
      _.liftTo[F](
        new Throwable("Mono to Effect Conversion failed to produce value")
      )
    )

  def fluxToStream[F[_]: Async, A](m: F[Flux[A]]): fs2.Stream[F, A] =
    Stream
      .eval(m)
      .flatMap(f => fromPublisher(publisherToFlowPublisher(f), 1))
      .chunks
      .flatMap(chunk => Stream.eval(Spawn[F].cede).flatMap(_ => Stream.chunk(chunk).covary[F]))
}
