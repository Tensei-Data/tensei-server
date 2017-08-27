/*
 * Copyright (C) 2014 - 2017  Contributors as noted in the AUTHORS.md file
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.wegtam.tensei.server

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import com.typesafe.config.ConfigFactory
import org.scalatest.{ BeforeAndAfterAll, FunSpecLike, Matchers }

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Base test class for testing actors.
  */
abstract class ActorSpec
    extends TestKit(ActorSystem("system", ConfigFactory.load()))
    with ImplicitSender
    with FunSpecLike
    with Matchers
    with BeforeAndAfterAll {

  /**
    * Shutdown actor system after tests and await termination.
    */
  override def afterAll(): Unit = {
    val _ = Await.result(system.terminate(), Duration.Inf)
  }
}
