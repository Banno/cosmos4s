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
import cats.effect.implicits._
import cats.syntax.all._
import fs2.interop.reactivestreams._
import fs2.Stream
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

object ReactorCore {
  def monoToEffectOpt[F[_]: ConcurrentEffect: ContextShift, A](m: F[Mono[A]]): F[Option[A]] =
    Stream
      .eval(m)
      .flatMap(fromPublisher[F, A])
      .compile
      .last
      .guarantee(ContextShift[F].shift)

  def monoToEffect[F[_]: ConcurrentEffect: ContextShift, A](m: F[Mono[A]]): F[A] =
    monoToEffectOpt(m).flatMap(opt =>
      opt.fold(
        Sync[F].raiseError[A](new Throwable("Mono to Effect Conversion failed to produce value"))
      ) {
        Sync[F].pure
      }
    )

  def fluxToStream[F[_]: ConcurrentEffect: ContextShift, A](m: F[Flux[A]]): fs2.Stream[F, A] =
    Stream
      .eval(m)
      .flatMap(fromPublisher[F, A])
      .chunks
      .flatMap(chunk =>
        Stream.eval(ContextShift[F].shift).flatMap(_ => Stream.chunk(chunk).covary[F])
      )
}
