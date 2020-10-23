package com.banno.cosmos4s

import cats._
import cats.effect._
import cats.syntax.all._
import io.circe.Json
import com.microsoft.azure.documentdb.{DocumentClient, PartitionKeyDefinition}
import com.microsoft.azure.documentdb.bulkexecutor.{BulkImportResponse, DocumentBulkExecutor}

trait BulkOps[F[_], V] {
  def insert(value: List[V]): F[Unit]
  def upsert(value: List[V]): F[Unit]

  def mapK[G[_]](fk: F ~> G): BulkOps[G, V] = new BulkOps.MapKBulkOps[F, G, V](this, fk)
  def contramapValue[A](f: A => V): BulkOps[F, A] = new BulkOps.ContramapValue[F, A, V](this, f)
}

object BulkOps {

  /**
   * https://docs.microsoft.com/en-us/azure/cosmos-db/bulk-executor-java
   * It is recommended to instantiate a single DocumentBulkExecutor object for the entire application
   * within a single virtual machine that corresponds to a specific Azure Cosmos container.
   */
  def impl[F[_]: Sync](
      client: DocumentClient,
      database: String,
      collection: String,
      partitionKey: PartitionKeyDefinition,
      offerThroughput: Int,
      maxConcurrencyPerPartitionRange: Int
  ): Resource[F, BulkOps[F, Json]] =
    Resource
      .liftF(
        Sync[F]
          .delay {
            DocumentBulkExecutor
              .builder()
              .from(client, database, collection, partitionKey, offerThroughput)
              .build
          }
      )
      .map(new Impl[F](_, maxConcurrencyPerPartitionRange))

  private class Impl[F[_]: Sync](
      executor: DocumentBulkExecutor,
      maxConcurrencyPerPartitionRange: Int
  ) extends BulkOps[F, Json] {
    import scala.collection.JavaConverters._

    def insert(value: List[Json]): F[Unit] =
      Sync[F].delay(
        executor.importAll(
          value.map(_.noSpaces).asJava,
          false,
          true,
          maxConcurrencyPerPartitionRange)) >>= { r =>
        if (r.getNumberOfDocumentsImported() == value.size)
          ().pure[F]
        else
          BulkInsertFailure(r).raiseError
      }

    def upsert(value: List[Json]): F[Unit] =
      Sync[F].delay(executor
        .importAll(value.map(_.noSpaces).asJava, true, true, maxConcurrencyPerPartitionRange)) >>= {
        r =>
          if (r.getNumberOfDocumentsImported() == value.size)
            ().pure[F]
          else
            BulkUpsertFailure(r).raiseError
      }

  }

  sealed trait BulkOpsFailure extends RuntimeException with Product with Serializable
  final case class BulkInsertFailure(response: BulkImportResponse) extends BulkOpsFailure
  final case class BulkUpsertFailure(response: BulkImportResponse) extends BulkOpsFailure

  private class MapKBulkOps[F[_], G[_], V](
      base: BulkOps[F, V],
      fk: F ~> G
  ) extends BulkOps[G, V] {
    def insert(value: List[V]): G[Unit] = fk(base.insert(value))
    def upsert(value: List[V]): G[Unit] = fk(base.upsert(value))
  }

  private class ContramapValue[F[_], V2, V](
      base: BulkOps[F, V],
      contra: V2 => V
  ) extends BulkOps[F, V2] {
    def insert(value: List[V2]): F[Unit] = base.insert(value.map(contra))
    def upsert(value: List[V2]): F[Unit] = base.upsert(value.map(contra))
  }

}
