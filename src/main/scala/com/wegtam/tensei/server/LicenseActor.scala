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

import akka.actor.{ Actor, ActorLogging, Cancellable, Props }
import com.wegtam.tensei.adt.TenseiLicenseMessages._
import com.wegtam.tensei.adt.{ ClusterConstants, TenseiLicense }
import com.wegtam.tensei.server.LicenseActor.UpdateChefAboutAgentCount

import scala.concurrent.duration._

object LicenseActor {

  def props(license: TenseiLicense) = Props(classOf[LicenseActor], license)

  case object UpdateChefAboutAgentCount

}

/**
  * A simple actor that holds a license and is used for restriction checks.
  */
class LicenseActor(license: TenseiLicense) extends Actor with ActorLogging {
  // Report the number of agents to the chef de cuisine in regular cycles.
  // A moderate interval should be enough (e.g. minutes or even hours).
  import context.dispatcher
  private val reportAllowedAgentsInterval: FiniteDuration = FiniteDuration(15, MINUTES)
  private val reportAllowedAgentsTimer: Cancellable =
    context.system.scheduler.schedule(reportAllowedAgentsInterval,
                                      reportAllowedAgentsInterval,
                                      self,
                                      UpdateChefAboutAgentCount)

  log.info("Running with license {} ({}).", license.id, license.licensee)

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    reportAllowedAgentsTimer.cancel()
    super.postStop()
  }

  override def receive: Receive = {
    case ReportLicenseEntitiesData =>
      sender() ! LicenseEntitiesData(license.agents,
                                     license.configurations,
                                     license.users,
                                     license.cronjobs,
                                     license.triggers)
    case ReportAllowedNumberOfAgents =>
      val allowedNumberOfAgents = if (license.expiresIn.isNegative) 0 else license.agents
      sender() ! AllowedNumberOfAgents(allowedNumberOfAgents)
    case ReportAllowedNumberOfConfigurations =>
      val allowedNumberOfConfigurations =
        if (license.expiresIn.isNegative) 0 else license.configurations
      sender() ! AllowedNumberOfConfigurations(allowedNumberOfConfigurations)
    case ReportAllowedNumberOfCronjobs =>
      val allowedNumberOfCronjobs = if (license.expiresIn.isNegative) 0 else license.cronjobs
      sender() ! AllowedNumberOfCronjobs(allowedNumberOfCronjobs)
    case ReportAllowedNumberOfTriggers =>
      val allowedNumberOfTriggers = if (license.expiresIn.isNegative) 0 else license.triggers
      sender() ! AllowedNumberOfTriggers(allowedNumberOfTriggers)
    case ReportAllowedNumberOfUsers =>
      val allowedNumberOfUsers = if (license.expiresIn.isNegative) 0 else license.users
      sender() ! AllowedNumberOfUsers(allowedNumberOfUsers)
    case ReportLicenseExpirationPeriod =>
      sender() ! LicenseExpiresIn(license.expiresIn)
    case ReportLicenseMetaData =>
      sender() ! LicenseMetaData(id = license.id,
                                 licensee = license.licensee,
                                 period = license.expiresIn)
    case UpdateChefAboutAgentCount =>
      val chef                  = context.actorSelection(s"/user/${ClusterConstants.topLevelActorNameOnServer}")
      val allowedNumberOfAgents = if (license.expiresIn.isNegative) 0 else license.agents
      chef ! AllowedNumberOfAgents(allowedNumberOfAgents)
  }
}
