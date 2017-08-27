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

import akka.actor._
import akka.cluster.ClusterEvent.{
  CurrentClusterState,
  MemberUp,
  ReachableMember,
  UnreachableMember
}
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.{ Cluster, Member, MemberStatus, UniqueAddress }
import com.wegtam.tensei.adt.{ ClusterConstants, GlobalMessages }
import com.wegtam.tensei.server.WatchDog.WatchDogMessages
import com.wegtam.tensei.server.WatchDog.WatchDogMessages.{
  AgentDown,
  CleanupUnreachableFrontendNodes,
  PingAgent,
  PingAgentTimeout
}

import scala.collection.immutable.Queue
import scala.concurrent.duration._

object WatchDog {
  sealed trait WatchDogMessages

  object WatchDogMessages {

    /**
      * This message is send if an agent is down.
      *
      * @param agentId   The ID of the agent.
      * @param timestamp The timestamp of the event.
      */
    final case class AgentDown(agentId: String, timestamp: Long) extends WatchDogMessages

    /**
      * This message is send if an agent comes up.
      *
      * @param agentId   The ID of the agent.
      * @param agentRef  The actor ref of the agent.
      * @param timestamp The timestamp of the event.
      */
    final case class AgentUp(agentId: String, agentRef: ActorRef, timestamp: Long)
        extends WatchDogMessages

    /**
      * Instruct the actor to remove all unreachable frontend nodes
      * from the cluster.
      */
    case object CleanupUnreachableFrontendNodes extends WatchDogMessages

    /**
      * Instruct the watchdog to ping the given agent.
      *
      * @param agentId   The ID of the agent.
      * @param agentPath The path to the agent.
      */
    final case class PingAgent(agentId: String, agentPath: ActorSelection) extends WatchDogMessages

    /**
      * Indicates that the ping timeout for an agent has been reached.
      *
      * @param agentId The ID of the agent.
      */
    final case class PingAgentTimeout(agentId: String) extends WatchDogMessages

    /**
      * This message is send if the watchdog becomes ready.
      */
    case object WatchDogReady extends WatchDogMessages

  }

  def props(): Props = Props(classOf[WatchDog])

  val name = "WatchDog"
}

/**
  * This simple actor subscribes to the cluster and waits for agents to join the cluster.
  * If an agent joins the cluster it will be watched by the watchdog.
  *
  */
class WatchDog extends Actor with ActorLogging {
  import context.dispatcher

  log.info("Agent watchdog started.")

  private val pingAgentTimeout = FiniteDuration(
    context.system.settings.config.getDuration("tensei.server.agent-ping-timeout", MILLISECONDS),
    MILLISECONDS
  )
  private val agentPingTimeoutSchedulers = scala.collection.mutable.Map.empty[String, Cancellable]
  private val unreachableFrontendCleanup = context.system.scheduler.schedule(
    FiniteDuration(15, SECONDS),
    FiniteDuration(30, SECONDS),
    self,
    CleanupUnreachableFrontendNodes
  )
  private var unreachableFrontendNodes: Queue[UniqueAddress] = Queue.empty

  def now: Long = System.currentTimeMillis()

  private val cluster = Cluster(context.system)

  // We try to address the chef by it's selection.
  private val chef = context.actorSelection(
    RootActorPath(cluster.selfAddress) / "user" / ClusterConstants.topLevelActorNameOnServer
  )

  ClusterClientReceptionist(context.system).registerService(self)

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberUp], classOf[UnreachableMember], classOf[ReachableMember])
    chef ! WatchDogMessages.WatchDogReady
  }

  override def postStop(): Unit = {
    unreachableFrontendCleanup.cancel()
    ClusterClientReceptionist(context.system).unregisterService(self)
    cluster.unsubscribe(self)
  }

  override def receive: Receive = {
    case GlobalMessages.ReportingTo(ref, id) =>
      log.debug("Got ReportingToCaller message from {}.", ref.path)
      handleReportingMessage(ref, id)
    case state: CurrentClusterState =>
      state.members.filter(_.status == MemberStatus.Up) foreach handelNewMember
    case MemberUp(member) =>
      handelNewMember(member)
    case UnreachableMember(member) =>
      handleUnreachableMember(member)
    case ReachableMember(member) =>
      handleReachableMember(member)
    case GlobalMessages.Shutdown =>
      log.debug("Received shutdown signal.")
      context stop self
    case PingAgent(agentId, agentPath) =>
      log.debug("Got ping agent request for {}.", agentId)
      agentPath ! GlobalMessages.ReportToCaller
      val t =
        context.system.scheduler.scheduleOnce(pingAgentTimeout, self, PingAgentTimeout(agentId))
      val _ = agentPingTimeoutSchedulers.put(agentId, t)
    case PingAgentTimeout(agentId) =>
      log.info("Ping timeout for agent {}.", agentId)
      chef ! AgentDown(agentId, now)
      val _ = agentPingTimeoutSchedulers.remove(agentId)
    case CleanupUnreachableFrontendNodes =>
      if (unreachableFrontendNodes.nonEmpty) {
        log.info("Removing {} unreachable frontend nodes from cluster.",
                 unreachableFrontendNodes.size)
        unreachableFrontendNodes.foreach(n => cluster.down(n.address))
        unreachableFrontendNodes = Queue.empty
      }
    case msg =>
      log.warning("Got unhandled message: {}", msg)
  }

  /**
    * Handle reporting messages.
    *
    * @param ref The actor ref passed by the reporting message.
    */
  def handleReportingMessage(ref: ActorRef, id: Option[String] = None): Unit =
    if (ref.path.name == ClusterConstants.topLevelActorNameOnAgent) {
      id.foreach { agentId =>
        log.info("Agent {} reporting in.", agentId)
        agentPingTimeoutSchedulers.remove(agentId).foreach(c => c.cancel())
        chef ! WatchDogMessages.AgentUp(agentId, ref, now)
      }
    }

  /**
    * Handle `MemberUp` events.
    * For members with the role `ClusterConstants.Roles.agent` send them an `Identify` message.
    *
    * @param member A cluster member.
    */
  def handelNewMember(member: Member): Unit =
    member.roles foreach {
      case ClusterConstants.Roles.agent =>
        log.info("New agent node joined the cluster from {}.", member.address)
        log.debug("Sending identify message to newly registered agent.")
        val agent = context.actorSelection(RootActorPath(member.address) / "user" / "TenseiAgent")
        agent ! GlobalMessages.ReportToCaller
      case ClusterConstants.Roles.frontend =>
        log.info("New frontend node joined the cluster from {}.", member.address)
      case ClusterConstants.Roles.server =>
        log.info("New server node joined the cluster from {}.", member.address)
      case ClusterConstants.Roles.watchdog =>
        log.info("New watchdog node joinded the cluster from {}.", member.address)
    }

  /**
    * Handle `ReachableMember` events.
    *
    * @param member A cluster member that is reachable again.
    */
  def handleReachableMember(member: Member): Unit =
    member.roles foreach {
      case ClusterConstants.Roles.agent =>
        log.info("Agent node reachable again.")
      case ClusterConstants.Roles.frontend =>
        log.info("Frontend node reachable again.")
        unreachableFrontendNodes = unreachableFrontendNodes.filterNot(_ == member.uniqueAddress)
      case ClusterConstants.Roles.server =>
        log.info("Server node reachable again.")
      case ClusterConstants.Roles.watchdog =>
        log.info("Watchdog node reachable again.")
    }

  /**
    * Handle `UnreachableMember` events.
    *
    * @param member A cluster member that is unreachable.
    */
  def handleUnreachableMember(member: Member): Unit =
    member.roles foreach {
      case ClusterConstants.Roles.agent =>
        log.warning("Agent node unreachable!")
      case ClusterConstants.Roles.frontend =>
        log.warning("Frontend node unreachable!")
        unreachableFrontendNodes = unreachableFrontendNodes :+ member.uniqueAddress
      case ClusterConstants.Roles.server =>
        log.warning("Server node unreachable!")
      case ClusterConstants.Roles.watchdog =>
        log.warning("Watchdog node unreachable!")
    }
}
