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

trait RawCosmosContainer[F[_], V] {
  def queryRaw(query: String, overrides: FeedOptions => FeedOptions = identity): Stream[F, V]
  def queryCustomRaw[A: Decoder](
      query: String,
      overrides: FeedOptions => FeedOptions = identity): Stream[F, A]

  def map[A](f: V => A): RawCosmosContainer[F, A] =
    new RawCosmosContainer.MapValueRawCosmosContainter(this, f)
  def evalMap[A](f: V => F[A]): RawCosmosContainer[F, A] =
    new RawCosmosContainer.EvalMapRawCosmosContainer(this, f)
  def mapK[G[_]](fk: F ~> G): RawCosmosContainer[G, V] =
    new RawCosmosContainer.MapKRawCosmosContainer(this, fk)
}

object RawCosmosContainer {
  def impl[F[_]: ConcurrentEffect: ContextShift](
      container: CosmosAsyncContainer,
      createFeedOptions: Option[F[FeedOptions]] = None): RawCosmosContainer[F, Json] =
    new BaseImpl[F](container, createFeedOptions)

  private class BaseImpl[F[_]: ConcurrentEffect: ContextShift](
      container: CosmosAsyncContainer,
      createFeedOptions: Option[F[FeedOptions]] = None)
      extends RawCosmosContainer[F, Json] {
    def createFeedOptionsAlways = createFeedOptions.getOrElse(Sync[F].delay(new FeedOptions()))
    import scala.collection.JavaConverters._
    def queryRaw(query: String, overrides: FeedOptions => FeedOptions = identity): Stream[F, Json] =
      queryCustomRaw[Json](query, overrides)
    def queryCustomRaw[A: Decoder](
        query: String,
        overrides: FeedOptions => FeedOptions = identity): Stream[F, A] =
      Stream
        .eval(createFeedOptionsAlways)
        .map(overrides)
        .flatMap { options =>
          ReactorCore.fluxToStream(
            Sync[F].delay(
              container
                .queryItems(query, options, classOf[JsonNode])
                .byPage()
            )
          )
        }
        .flatMap(page => Stream.fromIterator(page.getElements().iterator().asScala))
        .map(jacksonToCirce)
        .evalMap(_.as[A].liftTo[F])
  }

  private class MapKRawCosmosContainer[F[_], G[_], V](
      base: RawCosmosContainer[F, V],
      fk: F ~> G
  ) extends RawCosmosContainer[G, V] {
    def queryRaw(query: String, overrides: FeedOptions => FeedOptions = identity): Stream[G, V] =
      base.queryRaw(query, overrides).translate(fk)
    def queryCustomRaw[A: Decoder](
        query: String,
        overrides: FeedOptions => FeedOptions = identity): Stream[G, A] =
      base.queryCustomRaw(query, overrides).translate(fk)
  }

  private class MapValueRawCosmosContainter[F[_], V, A](
      base: RawCosmosContainer[F, V],
      f: V => A
  ) extends RawCosmosContainer[F, A] {
    def queryRaw(query: String, overrides: FeedOptions => FeedOptions = identity): Stream[F, A] =
      base.queryRaw(query, overrides).map(f)

    def queryCustomRaw[B: Decoder](
        query: String,
        overrides: FeedOptions => FeedOptions): Stream[F, B] =
      base.queryCustomRaw(query, overrides)
  }

  private class EvalMapRawCosmosContainer[F[_], V, A](
      base: RawCosmosContainer[F, V],
      f: V => F[A]
  ) extends RawCosmosContainer[F, A] {
    def queryRaw(query: String, overrides: FeedOptions => FeedOptions = identity): Stream[F, A] =
      base.queryRaw(query, overrides).evalMap(f)

    def queryCustomRaw[B: Decoder](
        query: String,
        overrides: FeedOptions => FeedOptions): Stream[F, B] =
      base.queryCustomRaw(query, overrides)
  }

  implicit def functor[F[_]]: Functor[RawCosmosContainer[F, *]] =
    new Functor[RawCosmosContainer[F, *]] {
      def map[A, B](fa: RawCosmosContainer[F, A])(f: A => B): RawCosmosContainer[F, B] = fa.map(f)
    }

}
