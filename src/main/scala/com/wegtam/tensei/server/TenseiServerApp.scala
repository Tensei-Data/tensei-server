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

import java.util.Locale

import akka.actor.{ ActorRef, ActorSystem }
import com.wegtam.tensei.adt.{ ClusterConstants, GlobalMessages }
import com.wegtam.tensei.server.ChefDeCuisine.ChefDeCuisineMessages
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{ Failure, Success, Try }

/**
  * The main entry point for the Tensei server component.
  *
  * It will try to start an actor system which represents the server
  * within the cluster.
  */
object TenseiServerApp {

  private final val log = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    val r = for {
      s <- Try(ActorSystem(ClusterConstants.systemName))
      c <- Try(s.actorOf(ChefDeCuisine.props(), ClusterConstants.topLevelActorNameOnServer))
      t <- Try {
        if (s.settings.config.hasPath("tensei.server.console") && s.settings.config.getBoolean(
              "tensei.server.console"
            )) {
          log.info("Running in interactive server mode.")
          printHelp()
          readLoop(c)
          Await.result(s.whenTerminated, FiniteDuration(30, SECONDS)) // Wait at max 30 seconds for shutdown.
        } else {
          Await.result(s.whenTerminated, Duration.Inf)
        }
      }
    } yield t
    r match {
      case Failure(error) =>
        log.error("An error occured!", error)
        sys.exit(1)
      case Success(_) =>
        sys.exit(0)
    }
  }

  /**
    * Print help for the interactive console mode.
    */
  def printHelp(): Unit = {
    log.info("Type 'agents' for a list of known agents.")
    log.info("Type 'help' or 'h' for help.")
    log.info("Type 'shutdown' or 's' to stop the server.")
  }

  /**
    * A simple loop that reads commands from standard input.
    *
    * It will understand and react on several commands and return
    * if the user issued the shutdown command.
    *
    * @param topActor The top level actor of the actor system.
    */
  @tailrec
  def readLoop(topActor: ActorRef): Unit =
    StdIn.readLine().toLowerCase(Locale.ROOT) match {
      case "agents" =>
        topActor ! ChefDeCuisineMessages.WriteAgentsInformationsToLog
        readLoop(topActor)
      case "help" | "h" =>
        printHelp()
        readLoop(topActor)
      case "shutdown" | "s" =>
        topActor ! GlobalMessages.Shutdown
      case cmd =>
        log.error("Unknown command '{}'!", cmd)
        readLoop(topActor)
    }
}
