package com.evolutiongaming.persistence

import com.evolutiongaming.sharding.ShardEntry

import scala.PartialFunction.condOpt

object PersistenceId {
  def apply(persistenceType: String, id: String): String = s"$persistenceType-$id"

  def apply(shardEntry: ShardEntry): String = PersistenceId(
    persistenceType = shardEntry.region.typeName,
    id = shardEntry.id)

  def unapply(persistenceId: String): Option[(String, String)] = condOpt(persistenceId split "-") {
    case Array(persistenceType, id) => persistenceType -> id
  }
}
