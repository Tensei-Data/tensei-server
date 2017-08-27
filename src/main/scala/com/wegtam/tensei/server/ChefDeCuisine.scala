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

import java.nio.file.FileSystems

import akka.actor._
import akka.cluster.client.ClusterClientReceptionist
import com.wegtam.tensei.adt.StatsMessages.{ CalculateStatistics, CalculateStatisticsResult }
import com.wegtam.tensei.adt._
import com.wegtam.tensei.agent.TenseiAgentState
import com.wegtam.tensei.server.ChefDeCuisine.{ ChefDeCuisineData, ChefDeCuisineMessages }
import com.wegtam.tensei.server.WatchDog.WatchDogMessages
import com.wegtam.tensei.server.helpers.ChefDeCuisineHelpers
import com.wegtam.tensei.server.suggesters.{ MappingSuggesterMessages, MappingSuggesterModes }

import scala.concurrent.duration._
import scalaz._
import Scalaz._

object ChefDeCuisine {

  def props(): Props = Props(classOf[ChefDeCuisine])

  sealed trait ChefDeCuisineMessages

  object ChefDeCuisineMessages {

    case object CleanupAgentsInformations extends ChefDeCuisineMessages

    case object PingAgents extends ChefDeCuisineMessages

    case class ReportTransformationConfigurations(ref: ActorRef) extends ChefDeCuisineMessages

    case class ReportTransformationConfigurationsResponse(ref: ActorRef)
        extends ChefDeCuisineMessages

    case object WriteAgentsInformationsToLog extends ChefDeCuisineMessages

  }

  /**
    * The state data of the chef de cuisine fsm.
    *
    * @param agents                      A map containing informations about each agent mapped to the agent id.
    * @param licenseActor                An actor ref pointing to an actor that holds the currently active license.
    * @param reportAgentsInformationsTo  A set of actor refs that will receive updates to agent informations.
    * @param watchdog                    An actor ref pointing to the watchdog actor.
    */
  case class ChefDeCuisineData(
      agents: Map[String, AgentInformation] = Map.empty[String, AgentInformation],
      licenseActor: Option[ActorRef] = None,
      reportAgentsInformationsTo: Set[ActorRef] = Set.empty[ActorRef],
      watchdog: Option[ActorRef] = None
  )
}

/**
  * This is the top level actor for the tensei server actor system.
  *
  * @todo Enable and implement akka persistence using CRDTs!
  */
class ChefDeCuisine
    extends Actor
    with FSM[ChefDeCuisineState, ChefDeCuisineData]
    with ActorLogging
    with ChefDeCuisineHelpers {
  val AGENT_CLEANUP_TIMER_NAME = "CleanupAgentInformations"
  val AGENT_PING_TIMER_NAME    = "PingAgents"
  val BOOT_TIMEOUT_TIMER_NAME  = "BootTimeout"
  val INIT_TIMEOUT_TIMER_NAME  = "InitTimeout"

  val agentCleanupInterval = FiniteDuration(
    context.system.settings.config
      .getDuration("tensei.server.agent-cleanup-interval", MILLISECONDS),
    MILLISECONDS
  )
  lazy val agentPingInterval = FiniteDuration(
    context.system.settings.config.getDuration("tensei.server.agent-ping-interval", MILLISECONDS),
    MILLISECONDS
  )
  lazy val agentPathResolveTimeout = FiniteDuration(
    context.system.settings.config
      .getDuration("tensei.server.agent-path-resolve-timeout", MILLISECONDS),
    MILLISECONDS
  )

  val bootTimeout = FiniteDuration(
    context.system.settings.config.getDuration("tensei.server.boot-timeout", MILLISECONDS),
    MILLISECONDS
  )

  val initTimeout = FiniteDuration(
    context.system.settings.config.getDuration("tensei.server.init-timeout", MILLISECONDS),
    MILLISECONDS
  )

  setTimer(AGENT_CLEANUP_TIMER_NAME,
           ChefDeCuisineMessages.CleanupAgentsInformations,
           agentCleanupInterval,
           repeat = true)

  setTimer(AGENT_PING_TIMER_NAME,
           ChefDeCuisineMessages.PingAgents,
           agentPingInterval,
           repeat = true)

  setTimer(BOOT_TIMEOUT_TIMER_NAME, StateTimeout, bootTimeout) // Set timer for boot mode because state timeout works only if no messages are received.

  ClusterClientReceptionist(context.system)
    .registerService(self) // Register as a service to be available from outside the cluster.

  startWith(ChefDeCuisineState.Booting, ChefDeCuisineData(licenseActor = startLicenseActor))

  // This mode is currently only a workaround for the fact that the `onTransition` helper
  // does not fire for the first state (`startWith(...)`).
  // After the boot timeout has been reached (either normally or triggerd by the timer) we switch
  // to initializiation mode.
  when(ChefDeCuisineState.Booting, stateTimeout = bootTimeout) {
    case Event(StateTimeout, data) =>
      log.info("Switching to initialization mode.")
      if (isTimerActive(BOOT_TIMEOUT_TIMER_NAME)) cancelTimer(BOOT_TIMEOUT_TIMER_NAME) // Cancel the boot timeout timer if it is still running.
      goto(ChefDeCuisineState.Initializing) using data
  }

  // Here we initialize needed resources like our `WatchDog`.
  when(ChefDeCuisineState.Initializing, stateTimeout = initTimeout) {
    case Event(WatchDogMessages.WatchDogReady, data) =>
      log.info("Watchdog is ready...")
      if (isTimerActive(INIT_TIMEOUT_TIMER_NAME)) cancelTimer(INIT_TIMEOUT_TIMER_NAME) // Cancel the timer to avoid a possible `StateTimout` message.
      goto(ChefDeCuisineState.Running) using data.copy(watchdog = Option(sender()))
    case Event(StateTimeout, data) =>
      log.debug("Reached init timeout.")
      if (data.watchdog.isEmpty) {
        log.warning("Unable to initialize watchdog within timeout. Retrying...")
        setTimer(INIT_TIMEOUT_TIMER_NAME, StateTimeout, initTimeout)
        goto(ChefDeCuisineState.Initializing) using data
      }
      goto(ChefDeCuisineState.Running) using data
  }

  when(ChefDeCuisineState.Running) {
    case Event(TenseiLicenseMessages.ReportLicenseEntitiesData, data) =>
      log.debug("Request for entities data of the license.")
      if (data.licenseActor.isDefined) {
        data.licenseActor.get.forward(TenseiLicenseMessages.ReportLicenseEntitiesData)
      } else
        sender() ! TenseiLicenseMessages.NoLicenseInstalled
      stay() using data
    case Event(TenseiLicenseMessages.ReportAllowedNumberOfAgents, data) =>
      log.debug("Request for number of allowed agents.")
      if (data.licenseActor.isDefined) {
        data.licenseActor.get.forward(TenseiLicenseMessages.ReportAllowedNumberOfAgents)
      } else
        sender() ! TenseiLicenseMessages.NoLicenseInstalled
      stay() using data
    case Event(TenseiLicenseMessages.ReportAllowedNumberOfConfigurations, data) =>
      log.debug("Request for number of allowed configurations.")
      if (data.licenseActor.isDefined) {
        data.licenseActor.get.forward(TenseiLicenseMessages.ReportAllowedNumberOfConfigurations)
      } else
        sender() ! TenseiLicenseMessages.NoLicenseInstalled
      stay() using data
    case Event(TenseiLicenseMessages.ReportAllowedNumberOfCronjobs, data) =>
      log.debug("Request for number of allowed cronjobs.")
      if (data.licenseActor.isDefined) {
        data.licenseActor.get.forward(TenseiLicenseMessages.ReportAllowedNumberOfCronjobs)
      } else
        sender() ! TenseiLicenseMessages.NoLicenseInstalled
      stay() using data
    case Event(TenseiLicenseMessages.ReportAllowedNumberOfTriggers, data) =>
      log.debug("Request for number of allowed triggers.")
      if (data.licenseActor.isDefined) {
        data.licenseActor.get.forward(TenseiLicenseMessages.ReportAllowedNumberOfTriggers)
      } else
        sender() ! TenseiLicenseMessages.NoLicenseInstalled
      stay() using data
    case Event(TenseiLicenseMessages.ReportAllowedNumberOfUsers, data) =>
      log.debug("Request for number of allowed users.")
      if (data.licenseActor.isDefined) {
        data.licenseActor.get.forward(TenseiLicenseMessages.ReportAllowedNumberOfUsers)
      } else
        sender() ! TenseiLicenseMessages.NoLicenseInstalled
      stay() using data
    case Event(TenseiLicenseMessages.ReportLicenseExpirationPeriod, data) =>
      log.debug("Received request for license expiration period.")
      if (data.licenseActor.isDefined)
        data.licenseActor.get.forward(TenseiLicenseMessages.ReportLicenseExpirationPeriod)
      else
        sender() ! TenseiLicenseMessages.NoLicenseInstalled
      stay() using data
    case Event(TenseiLicenseMessages.ReportLicenseMetaData, data) =>
      log.debug("Received request for license meta data.")
      if (data.licenseActor.isDefined)
        data.licenseActor.get.forward(TenseiLicenseMessages.ReportLicenseMetaData)
      else
        sender() ! TenseiLicenseMessages.NoLicenseInstalled
      stay() using data
    case Event(TenseiLicenseMessages.UpdateLicense(licenseString), data) =>
      log.info("Received update license message.")
      data.licenseActor.foreach(ref => context.stop(ref)) // Remove running license actor.
      val msg: TenseiLicenseMessages.UpdateLicenseResult = updateLicenseFile(licenseString) match {
        case scala.util.Failure(t) =>
          log.error(t, "Could not update license file!")
          TenseiLicenseMessages.UpdateLicenseResult(t.getLocalizedMessage.left[String])
        case scala.util.Success(p) =>
          log.debug("Updated license file at {}.", p)
          TenseiLicenseMessages.UpdateLicenseResult("License updated.".right[String])
      }
      sender() ! msg
      val nData = startLicenseActor match {
        case None =>
          self ! TenseiLicenseMessages.AllowedNumberOfAgents(0) // No license == no agents!
          data.copy(licenseActor = None)
        case Some(ref) =>
          ref ! TenseiLicenseMessages.ReportAllowedNumberOfAgents
          data.copy(licenseActor = Option(ref))
      }
      stay() using nData
    case Event(TenseiLicenseMessages.ValidateLicense(licenseString), data) =>
      log.debug("Received validate license message.")
      val validationResult = validateEncodedLicense(licenseString)
      sender() ! TenseiLicenseMessages.ValidateLicenseResult(validationResult)
      stay() using data
    case Event(WatchDogMessages.AgentDown(agentId, timestamp), data) =>
      log.debug("Watchdog notified us about downed agent '{}'.", agentId)
      val entry = data.agents.get(agentId)
      entry
        .map { info =>
          val newAgentInformations: Map[String, AgentInformation] =
            if (entry.exists(_.auth == AgentAuthorizationState.Disconnected))
              data.agents // The entry is already marked disconnected.
            else
              data.agents + (agentId -> info.copy(
                auth = AgentAuthorizationState.Disconnected,
                lastUpdated = timestamp
              )) // Update state and timestamp.

          stay() using data.copy(agents = newAgentInformations)
        }
        .getOrElse {
          log.info("Got agent down message for agent '{}' which is not in our agent cache!",
                   agentId)
          stay() using data
        }
    case Event(WatchDogMessages.AgentUp(agentId, agentRef, timestamp), data) =>
      log.debug("Watchdog notified us about agent up at {}.", agentRef.path)
      // If the agent exists in our cache we update it's entry otherwise we create a new agent information entry.
      val agentInformation: AgentInformation = data.agents
        .get(agentId)
        .map(
          info =>
            if (info.auth == AgentAuthorizationState.Unauthorized)
              info.copy(lastUpdated = timestamp) // Leave an unauthorised agent in the state.
            else
              info.copy(auth = AgentAuthorizationState.Connected, lastUpdated = timestamp) // Otherwise set the state to "connected".
        )
        .getOrElse(
          AgentInformation(agentId,
                           agentRef.path.toSerializationFormatWithAddress(agentRef.path.address),
                           AgentAuthorizationState.Unauthorized,
                           timestamp)
        )
      if (data.licenseActor.isDefined) {
        data.licenseActor.get ! TenseiLicenseMessages.ReportAllowedNumberOfAgents
      } else
        log.error("No license actor available to authorise agents!")

      stay() using data.copy(agents = data.agents + (agentId -> agentInformation))
    case Event(TenseiLicenseMessages.AllowedNumberOfAgents(numberOfAgents), data) =>
      log.debug("Got allowed number of agents({}) from license actor.", numberOfAgents)
      val updatedAgents = authorizeAgents(data.agents, numberOfAgents)
      stay() using data.copy(agents = updatedAgents)
    case Event(stateUpdate: AgentWorkingState, data) =>
      log.debug("Got updated agent working state from '{}'.", sender().path)
      val entry = data.agents.find(_._1 == stateUpdate.id)
      if (entry.isDefined) {
        val newInfo = data.agents(stateUpdate.id).copy(workingState = Option(stateUpdate))
        val newData = data.copy(agents = data.agents + (stateUpdate.id -> newInfo))
        reportAgentsInformations(newData)
        stay() using newData
      } else {
        log.warning("Got information update from unknown agent '{}'!", stateUpdate.id)
        if (data.watchdog.isDefined) sender() ! GlobalMessages.ReportToRef(data.watchdog.get) // Force re-registering of agent to watchdog.
        stay() using data
      }
    case Event(MappingSuggesterMessages.SuggestMapping(cookbook, mode, answerTo), data) =>
      val receiver = answerTo getOrElse sender()
      mode match {
        case MappingSuggesterModes.AdvancedSemantics =>
          val suggester = context.actorOf(AdvancedSemanticsMappingSuggesterActor.props())
          suggester ! MappingSuggesterMessages.SuggestMapping(cookbook, mode, Option(receiver))
        case MappingSuggesterModes.Simple =>
          val suggester = context.actorOf(SimpleMappingSuggesterActor.props())
          suggester ! MappingSuggesterMessages.SuggestMapping(cookbook, mode, Option(receiver))
        case MappingSuggesterModes.SimpleSemantics =>
          val suggester = context.actorOf(SemanticMappingSuggesterActor.props())
          suggester ! MappingSuggesterMessages.SuggestMapping(cookbook, mode, Option(receiver))
        case _ =>
          log.warning("Unhandled suggesting mode!")
      }
      stay() using data
    case Event(msg: CalculateStatistics, data) =>
      log.info("Got statistical analysis message!")
      val availableAgents =
        getAgentsInSpecificState(getConnectedAgents(data.agents), TenseiAgentState.Idle)
      if (availableAgents.isEmpty) {
        log.info("No agent is available for a statistical analysis.")
        val result = CalculateStatisticsResult(
          "No agent free for statistics calculation!".left[List[StatsResult]],
          msg.source,
          msg.cookbook,
          msg.sourceIds,
          msg.percent
        )
        sender() ! result
      } else {
        log.info("Sent the statistical analysis request to an idle agent.")
        val agentSelection = context.actorSelection(availableAgents.head._2.path)
        agentSelection.forward(msg)
      }
      stay() using data

    case Event(ServerMessages.StartTransformationConfiguration(message), data) =>
      log.debug("Trying to start transformation configuration.")
      if (message.isDefined) {
        val availableAgents =
          getAgentsInSpecificState(getConnectedAgents(data.agents), TenseiAgentState.Idle)
        if (availableAgents.isEmpty) {
          log.info("No Agent available for tk: {}", message.get.uniqueIdentifier.get) // DEBUG
          val error: StatusMessage = new StatusMessage(
            reporter = Option(self.path.toSerializationFormatWithAddress(self.path.address)),
            message = "No agent available!",
            statusType = StatusType.NoAgentAvailable,
            cause = None
          )
          sender() ! ServerMessages.StartTransformationConfigurationResponse(
            statusMessage = error.left[String],
            message.get.uniqueIdentifier
          )
        } else {
          log.info("An agent is available for tk: {}", message.get.uniqueIdentifier.get) // DEBUG
          val agentSelection = context.actorSelection(availableAgents.head._2.path)
          agentSelection ! message.get
          val response = s"Transformation started on agent ${availableAgents.head._1}."
          sender() ! ServerMessages.StartTransformationConfigurationResponse(
            statusMessage = response.right[StatusMessage],
            message.get.uniqueIdentifier
          )

          val agentId = availableAgents.head._2.id
          val entry   = data.agents.find(_._1 == agentId)
          if (entry.isDefined) {
            val newState =
              data.agents(agentId).workingState.get.copy(state = TenseiAgentState.Working)
            val newInfo = data.agents(agentId).copy(workingState = Option(newState))
            val newData = data.copy(agents = data.agents + (agentId -> newInfo))
            reportAgentsInformations(newData)
            stay() using newData
          }
        }
      }
      stay() using data
    case Event(msg: GlobalMessages.ExtractSchema, data) =>
      log.debug("Got request for schema extration.")
      val availableAgents =
        getAgentsInSpecificState(getConnectedAgents(data.agents), TenseiAgentState.Idle)
      if (availableAgents.isEmpty) {
        sender() ! GlobalMessages.ExtractSchemaResult(msg.source,
                                                      "No agent available!".left[DFASDL])
      } else {
        val agentSelection = context.actorSelection(availableAgents.head._2.path)
        agentSelection.forward(msg)
      }
      stay() using data
    case Event(msg: GlobalMessages.TransformationCompleted, data) =>
      log.info("Got transformation completed message from agent")
      if (msg.uuid.isDefined) {
        log.info("Agent completed transformation configuration with uuid: {}", msg.uuid.get)
        data.reportAgentsInformationsTo foreach (ref => ref ! msg)
      }
      stay() using data
    case Event(msg: GlobalMessages.TransformationAborted, data) =>
      log.info("Got transformation aborted message from agent")
      if (msg.uuid.isDefined) {
        log.info("Agent aborted transformation configuration with uuid: {}", msg.uuid.get)
        data.reportAgentsInformationsTo foreach (ref => ref ! msg)
      }
      stay() using data
    case Event(msg: GlobalMessages.TransformationError, data) =>
      log.info("Got transformation error message from agent")
      if (msg.uuid.isDefined) {
        log.info("Agent sent error message for transformation configuration with uuid: {}",
                 msg.uuid.get)
        data.reportAgentsInformationsTo foreach (ref => ref ! msg)
      }
      stay() using data
    case Event(msg: GlobalMessages.TransformationStarted, data) =>
      log.info("Got transformation started message from agent")
      if (msg.uuid.isDefined) {
        log.info("Agent started transformation configuration with uuid: {}", msg.uuid.get)
        data.reportAgentsInformationsTo foreach (ref => ref ! msg)
      }
      stay() using data
    case Event(msg: GlobalMessages.RequestAgentRunLogsMetaData, data) =>
      log.debug("Forwarding agent run logs meta data request to all agents.")
      getConnectedAgents(data.agents)
        .foreach(info => context.actorSelection(info._2.path).forward(msg))
      stay() using data
    case Event(msg: GlobalMessages.RequestAgentRunLogs, data) =>
      log.debug("Forwarding agent run logs request to agent.")
      getConnectedAgents(data.agents)
        .filter(e => e._1 == msg.agentId)
        .foreach(info => context.actorSelection(info._2.path).forward(msg))
      stay() using data
  }

  whenUnhandled {
    case Event(Terminated(ref), data) =>
      if (ref == data.watchdog.get) {
        log.info("Watchdog crashed, restarting it.")
        goto(ChefDeCuisineState.Initializing) using data.copy(watchdog = None)
      } else if (data.reportAgentsInformationsTo.contains(ref)) {
        log.debug("Removing terminated actor from reportAgentsInformationTo.")
        val cleanedRefs = data.reportAgentsInformationsTo.filterNot(e => e == ref)
        stay() using data.copy(reportAgentsInformationsTo = cleanedRefs)
      } else {
        log.warning("Got unhandled actor termination message from '{}'!", ref.path)
        stay() using data
      }
    case Event(ChefDeCuisineMessages.CleanupAgentsInformations, data) =>
      log.debug("Cleaning cached agent informations.")
      val now = System.currentTimeMillis()
      val removeAgentAfterInterval = context.system.settings.config
        .getDuration("tensei.server.remove-unreachable-agents-after", MILLISECONDS)
      val tooOldTimestamp = now - removeAgentAfterInterval
      val filteredAgents = data.agents.filterNot(
        e => e._2.auth == AgentAuthorizationState.Disconnected && e._2.lastUpdated < tooOldTimestamp
      )
      stay() using data.copy(agents = filteredAgents)
    case Event(ChefDeCuisineMessages.PingAgents, data) =>
      log.debug("Received ping agents message.")
      data.watchdog.foreach(
        watchdog =>
          data.agents.foreach(
            entry =>
              watchdog ! WatchDogMessages.PingAgent(
                agentId = entry._1,
                agentPath = context.actorSelection(entry._2.path)
            )
        )
      )
      stay() using data
    case Event(ChefDeCuisineMessages.WriteAgentsInformationsToLog, data) =>
      log.info("Writing agent informations to log file on request of {}.", sender().path)
      log.info("AGENTS: {}", data.agents.mkString(", "))
      stay() using data
    case Event(GlobalMessages.Shutdown, _) =>
      log.warning("Got shutdown signal. Trying to shutdown actor system.")
      context.system.terminate()
      stop(FSM.Shutdown)
    case Event(GlobalMessages.ReportToRef(ref), data) =>
      log.info("Got ReportToRef({}) signal from {}.", ref.path, sender().path)
      val newData = data.copy(reportAgentsInformationsTo = data.reportAgentsInformationsTo + ref)
      context watch ref
      ref ! GlobalMessages.ReportingTo(self)
      stay() using newData
    case Event(ServerMessages.ReportAgentsInformations, data) =>
      log.debug("Reporting agents informations.")
      sender() ! ServerMessages.ReportAgentsInformationsResponse(data.agents) // Send informations at once.
      stay() using data
  }

  onTransition {
    case _ -> ChefDeCuisineState.Initializing =>
      setTimer(INIT_TIMEOUT_TIMER_NAME, StateTimeout, initTimeout) // Set timer for init mode because state timeout works only if no messages are received.
      if (stateData.watchdog.isEmpty) {
        log.debug("Watchdog not initialized, starting one now.")
        val _ = context.actorOf(WatchDog.props(), WatchDog.name)
      }
  }

  initialize()

  /**
    * Loads the license from the license file and starts the license actor.
    * If everything was successfull an option to the actor ref of the license
    * actor is returned.
    *
    * @return An option to the actor ref of the license actor.
    */
  private def startLicenseActor: Option[ActorRef] = {
    val fs   = FileSystems.getDefault
    val prop = loadFileContent(fs.getPath(LICENSE))
    val free = loadFileContent(fs.getPath(FREE_LICENSE))
    val propL = for {
      p <- prop
      pl = decryptBase64EncodedLicense(p)
    } yield pl
    val freeL = for {
      f <- free
      fl = decryptBase64EncodedLicense(f)
    } yield fl
    val licenseData = propL match {
      case scala.util.Failure(_) => freeL.toOption
      case scala.util.Success(vr) =>
        vr match {
          case (LicenseValidationResult.Valid, Some(_)) => propL.toOption
          case _                                        => freeL.toOption
        }
    }
    licenseData match {
      case None =>
        log.error("Unable to load a license file!")
        None
      case Some((_, None)) =>
        log.error("Unable to decrypt encoded license!")
        None
      case Some((vr, Some(license))) =>
        vr match {
          case LicenseValidationResult.Valid =>
            Option(context.actorOf(LicenseActor.props(license)))
          case LicenseValidationResult.Invalid(reason) =>
            log.error("Tensei license is invalid! Reason: {}",
                      reason.getOrElse(InvalidLicenseReason.Damaged))
            None
        }
    }
  }

  /**
    * Sends the agents information to every actor ref included in the `reportAgentsInformationsTo` set.
    *
    * @param data The current state's data.
    */
  private def reportAgentsInformations(data: ChefDeCuisineData): Unit =
    data.reportAgentsInformationsTo foreach (
        ref => ref ! ServerMessages.ReportAgentsInformationsResponse(data.agents)
    )
}
