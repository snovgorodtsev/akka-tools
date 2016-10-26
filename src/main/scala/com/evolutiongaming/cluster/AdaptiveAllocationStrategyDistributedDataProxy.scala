package com.evolutiongaming.cluster

import akka.actor.{ActorRef, ActorLogging, Actor}
import akka.cluster.Cluster
import akka.cluster.ddata._
import akka.cluster.ddata.Replicator._

import scala.compat.Platform

class AdaptiveAllocationStrategyDistributedDataProxy extends Actor with ActorLogging {

  import AdaptiveAllocationStrategy._
  import AdaptiveAllocationStrategyDistributedDataProxy._

  implicit val node = Cluster(context.system)
  val selfAddress = node.selfAddress
  lazy val replicator: ActorRef = DistributedData(context.system).replicator

  replicator ! Subscribe(EntityToNodeCountersKey, self)

  def sendBindingUpdate(entityKey: String, counterKey: String) =
    replicator ! Update(EntityToNodeCountersKey, ORMultiMap.empty[String], WriteLocal)(_ addBinding (entityKey, counterKey))

  def receive = {
    case Increment(typeName, id) =>
      val entityKey = genEntityKey(typeName, id)
      val counterKey = genCounterKey(entityKey, selfAddress.toString)
      counters get counterKey match {
        case None =>
          counters += (counterKey -> ValueData(1, Platform.currentTime))
          replicator ! Subscribe(PNCounterKey(counterKey), self)
        case _    =>
      }
      replicator ! Update(PNCounterKey(counterKey), PNCounter.empty, WriteLocal)(_ + 1)

      entityToNodeCounters get entityKey match {
        case Some(counterKeys) if counterKeys contains counterKey =>

        case Some(counterKeys)                                        =>
          entityToNodeCounters = entityToNodeCounters + (entityKey -> (counterKeys + counterKey))
          sendBindingUpdate(entityKey, counterKey)

        case None                                                     =>
          entityToNodeCounters = entityToNodeCounters + (entityKey -> Set(counterKey))
          sendBindingUpdate(entityKey, counterKey)
      }

    case Clear(typeName, id) =>
      val entityKey = genEntityKey(typeName, id)
      entityToNodeCounters get entityKey match {
        case Some(counterKeys) =>
          for (counterKey <- counterKeys) {
            counters get counterKey match {
              case None    =>
                replicator ! Subscribe(PNCounterKey(counterKey), self)

              case Some(v) =>
                if (v.value.isValidLong) {
                  counters += (counterKey -> ValueData(0, Platform.currentTime))
                  replicator ! Update(PNCounterKey(counterKey), PNCounter.empty, WriteLocal)(_ - v.value.longValue())
                } else {
                  // probably should never happen
                  log warning s"Can't clear counter for key $counterKey - it's bigger than Long"
                }
            }
          }
        case None              =>
      }

    case UpdateSuccess(_, _) =>

    case UpdateTimeout(key, _) =>
      // probably should never happen for local updates
      log warning s"Update timeout for key $key"

    case c@Changed(key: PNCounterKey) =>
      val newValue = (c get key).value
      counters get key._id match {
        case None           =>
          counters += (key._id -> ValueData(newValue, Platform.currentTime))
        case Some(oldValue) =>
          counters += (key._id -> ValueData(newValue, oldValue.cleared))
      }

    case c@Changed(EntityToNodeCountersKey) =>
      val newData = (c get EntityToNodeCountersKey).entries
      val oldCounterKeys = entityToNodeCounters.values.flatten.toSet
      entityToNodeCounters = newData
      val newCounterKeys = newData.values.flatten.toSet
      val diffKeys = newCounterKeys diff oldCounterKeys
      for (key <- diffKeys) replicator ! Subscribe(PNCounterKey(key), self)
  }
}

object AdaptiveAllocationStrategyDistributedDataProxy {
  // DData key of the entityToNodeCounterIds map
  private[cluster] val EntityToNodeCountersKey = ORMultiMapKey[String]("EntityToNodeCounters")
}
