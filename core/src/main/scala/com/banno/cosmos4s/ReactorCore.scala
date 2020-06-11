package com.banno.cosmos4s

import cats.implicits._
import cats.effect._
import cats.effect.implicits._
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import fs2._
import fs2.interop.reactivestreams._

private[cosmos4s] object ReactorCore {
  def monoToEffectOpt[F[_]: ConcurrentEffect: ContextShift, A](m: F[Mono[A]]): F[Option[A]] = {
    Stream.eval(m)
      .flatMap(fromPublisher[F, A])
      .compile
      .last
  }.guarantee(ContextShift[F].shift)

  def monoToEffect[F[_]: ConcurrentEffect: ContextShift, A](m: F[Mono[A]]): F[A] = 
    monoToEffectOpt(m).flatMap(opt => 
      opt.fold(Sync[F].raiseError[A](new Throwable("Mono to Effect Conversion failed to produce value"))){
        Sync[F].pure
      }
    )
  def fluxToStream[F[_]: ConcurrentEffect: ContextShift, A](m: F[Flux[A]]): fs2.Stream[F, A] = {
    Stream.eval(m)
      .flatMap(fromPublisher[F, A])
      .chunks
      .flatMap(chunk => Stream.eval(ContextShift[F].shift).flatMap(_ => Stream.chunk(chunk).covary[F]))
  }
}