package com.evolutiongaming.test

import _root_.com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Suite}

trait ActorSpec extends BeforeAndAfterAll { this: Suite =>

  implicit lazy val system: ActorSystem = ActorSystem(getClass.getSimpleName, config)

  def config = ConfigFactory.load()

  override protected def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  abstract class ActorScope extends TestKit(system) with ImplicitSender with DefaultTimeout
}