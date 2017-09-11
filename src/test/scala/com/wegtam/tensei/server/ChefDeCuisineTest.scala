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

import akka.testkit.{ TestActorRef, TestFSMRef }
import com.wegtam.tensei.adt.AgentAuthorizationState.{ Disconnected, Unauthorized }
import com.wegtam.tensei.adt._
import com.wegtam.tensei.server.ChefDeCuisine.ChefDeCuisineData
import com.wegtam.tensei.server.ServerMessages.{
  ReportAgentsInformations,
  ReportAgentsInformationsResponse
}
import com.wegtam.tensei.server.WatchDog.WatchDogMessages
import org.scalatest.BeforeAndAfterEach

class ChefDeCuisineTest extends ActorSpec with BeforeAndAfterEach {
  val chef = TestFSMRef(new ChefDeCuisine())

  override def beforeEach(): Unit =
    chef.setState(ChefDeCuisineState.Running, ChefDeCuisineData.initialise(licenseActor = None))

  describe("ChefDeCuisine") {
    describe("when receiving AgentUp") {
      describe("without a license actor") {
        it("should update the agent information list with an unauthorized agent") {
          val dummy     = TestActorRef(DummyActor.props())
          val timestamp = System.currentTimeMillis()
          val expectedInformation =
            AgentInformation("Dummy-01",
                             dummy.path.toSerializationFormatWithAddress(dummy.path.address),
                             Unauthorized,
                             timestamp)
          chef ! WatchDogMessages.AgentUp("Dummy-01", dummy, timestamp)
          chef ! ReportAgentsInformations

          val response = expectMsgType[ReportAgentsInformationsResponse]
          response.agents.get("Dummy-01").get shouldEqual expectedInformation
        }
      }

      describe("with a license actor") {
        it(
          "should update the agent information list with an unauthorized agent and ask the license actor"
        ) {
          chef.setState(ChefDeCuisineState.Running,
                        ChefDeCuisineData.initialise(licenseActor = Option(self)))
          val dummy     = TestActorRef(DummyActor.props())
          val timestamp = System.currentTimeMillis()
          val expectedInformation =
            AgentInformation("Dummy-01",
                             dummy.path.toSerializationFormatWithAddress(dummy.path.address),
                             Unauthorized,
                             timestamp)
          chef ! WatchDogMessages.AgentUp("Dummy-01", dummy, timestamp)

          expectMsg(TenseiLicenseMessages.ReportAllowedNumberOfAgents)

          chef ! ReportAgentsInformations
          val response = expectMsgType[ReportAgentsInformationsResponse]
          response.agents.get("Dummy-01").get shouldEqual expectedInformation
        }
      }
    }

    describe("when receiving AgentDown") {
      it("should update the agent information list with a disconnected agent") {
        val dummy = TestActorRef(DummyActor.props())
        chef ! WatchDogMessages.AgentUp("Dummy-02", dummy, System.currentTimeMillis() - 10)
        val timestamp = System.currentTimeMillis()
        val expectedInformation =
          AgentInformation("Dummy-02",
                           dummy.path.toSerializationFormatWithAddress(dummy.path.address),
                           Disconnected,
                           timestamp)
        chef ! WatchDogMessages.AgentDown("Dummy-02", timestamp)
        chef ! ReportAgentsInformations

        val response = expectMsgType[ReportAgentsInformationsResponse]
        response.agents.get("Dummy-02").get shouldEqual expectedInformation
      }
    }

    describe("when receiving ReportAgentsInformations") {
      it("should respond with a ReportAgentsResponse") {
        chef ! ReportAgentsInformations
        expectMsgType[ReportAgentsInformationsResponse]
      }

      it("should notify us on agent updates") {
        val dummy = TestActorRef(DummyActor.props())

        chef ! WatchDogMessages.AgentUp("DUMMY", dummy, System.currentTimeMillis())
        chef ! ReportAgentsInformations
        expectMsgType[ReportAgentsInformationsResponse]
      }
    }
  }
}
