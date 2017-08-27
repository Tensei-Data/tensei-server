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

package com.wegtam.tensei.server.helpers

import com.wegtam.tensei.adt._
import com.wegtam.tensei.agent.{ ParserState, ProcessorState, TenseiAgentState }
import com.wegtam.tensei.server.DefaultSpec

class ChefDeCuisineHelpersTest extends DefaultSpec with ChefDeCuisineHelpers {
  describe("ChefDeCuisineHelpers") {
    describe("authorizeAgents") {
      describe("if more agents should be authorized than there are present") {
        it("should authorize all agents") {
          val timestamp = System.currentTimeMillis()

          val agents = Map(
            "1" -> AgentInformation("1", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "2" -> AgentInformation("2", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "3" -> AgentInformation("3", "PATH", AgentAuthorizationState.Connected, timestamp),
            "4" -> AgentInformation("4", "PATH", AgentAuthorizationState.Disconnected, timestamp),
            "5" -> AgentInformation("5", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "6" -> AgentInformation("6", "PATH", AgentAuthorizationState.Connected, timestamp)
          )

          val expectedAgents = Map(
            "1" -> AgentInformation("1", "PATH", AgentAuthorizationState.Connected, timestamp),
            "2" -> AgentInformation("2", "PATH", AgentAuthorizationState.Connected, timestamp),
            "3" -> AgentInformation("3", "PATH", AgentAuthorizationState.Connected, timestamp),
            "4" -> AgentInformation("4", "PATH", AgentAuthorizationState.Disconnected, timestamp),
            "5" -> AgentInformation("5", "PATH", AgentAuthorizationState.Connected, timestamp),
            "6" -> AgentInformation("6", "PATH", AgentAuthorizationState.Connected, timestamp)
          )

          authorizeAgents(agents, agents.size + 1) should be(expectedAgents)
        }
      }

      describe("if the correct number of agents is already authorized") {
        it("should do nothing") {
          val timestamp = System.currentTimeMillis()

          val agents = Map(
            "1" -> AgentInformation("1", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "2" -> AgentInformation("2", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "3" -> AgentInformation("3", "PATH", AgentAuthorizationState.Connected, timestamp),
            "4" -> AgentInformation("4", "PATH", AgentAuthorizationState.Disconnected, timestamp),
            "5" -> AgentInformation("5", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "6" -> AgentInformation("6", "PATH", AgentAuthorizationState.Connected, timestamp)
          )

          val expectedAgents = Map(
            "1" -> AgentInformation("1", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "2" -> AgentInformation("2", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "3" -> AgentInformation("3", "PATH", AgentAuthorizationState.Connected, timestamp),
            "4" -> AgentInformation("4", "PATH", AgentAuthorizationState.Disconnected, timestamp),
            "5" -> AgentInformation("5", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "6" -> AgentInformation("6", "PATH", AgentAuthorizationState.Connected, timestamp)
          )

          authorizeAgents(agents, agents.count(_._2.auth != AgentAuthorizationState.Unauthorized)) should be(
            expectedAgents
          )
        }
      }

      describe("if agents need to be unauthorized") {
        it("should unauthorize the correct number of agents") {
          val timestamp = System.currentTimeMillis()

          val agents = Map(
            "1" -> AgentInformation("1", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "2" -> AgentInformation("2", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "3" -> AgentInformation("3", "PATH", AgentAuthorizationState.Connected, timestamp),
            "4" -> AgentInformation("4", "PATH", AgentAuthorizationState.Disconnected, timestamp),
            "5" -> AgentInformation("5", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "6" -> AgentInformation("6", "PATH", AgentAuthorizationState.Connected, timestamp)
          )

          val expectedAgents = Map(
            "1" -> AgentInformation("1", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "2" -> AgentInformation("2", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "3" -> AgentInformation("3", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "4" -> AgentInformation("4", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "5" -> AgentInformation("5", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "6" -> AgentInformation("6", "PATH", AgentAuthorizationState.Connected, timestamp)
          )

          val result = authorizeAgents(agents, 1)
          result.count(_._2.auth != AgentAuthorizationState.Unauthorized) should be(
            expectedAgents.count(_._2.auth != AgentAuthorizationState.Unauthorized)
          )
          result.count(_._2.auth == AgentAuthorizationState.Unauthorized) should be(
            expectedAgents.count(_._2.auth == AgentAuthorizationState.Unauthorized)
          )
        }
      }

      describe("if agents need to be authorized") {
        it("should set the correct number of agents to Connected") {
          val timestamp = System.currentTimeMillis()

          val agents = Map(
            "1" -> AgentInformation("1", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "2" -> AgentInformation("2", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "3" -> AgentInformation("3", "PATH", AgentAuthorizationState.Connected, timestamp),
            "4" -> AgentInformation("4", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "5" -> AgentInformation("5", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "6" -> AgentInformation("6", "PATH", AgentAuthorizationState.Connected, timestamp)
          )

          val expectedAgents = Map(
            "1" -> AgentInformation("1", "PATH", AgentAuthorizationState.Connected, timestamp),
            "2" -> AgentInformation("2", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "3" -> AgentInformation("3", "PATH", AgentAuthorizationState.Connected, timestamp),
            "4" -> AgentInformation("4", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "5" -> AgentInformation("5", "PATH", AgentAuthorizationState.Unauthorized, timestamp),
            "6" -> AgentInformation("6", "PATH", AgentAuthorizationState.Connected, timestamp)
          )

          val result = authorizeAgents(agents, 3)
          result.count(_._2.auth != AgentAuthorizationState.Unauthorized) should be(
            expectedAgents.count(_._2.auth != AgentAuthorizationState.Unauthorized)
          )
          result.count(_._2.auth == AgentAuthorizationState.Unauthorized) should be(
            expectedAgents.count(_._2.auth == AgentAuthorizationState.Unauthorized)
          )
        }
      }
    }

    describe("convertHexStringToByteArray") {
      it("should convert valid hex strings correctly") {
        val sourceString = "0102030405060708090a0b0c0d0e0f"
        val expectedArray = Array(1.toByte,
                                  2.toByte,
                                  3.toByte,
                                  4.toByte,
                                  5.toByte,
                                  6.toByte,
                                  7.toByte,
                                  8.toByte,
                                  9.toByte,
                                  10.toByte,
                                  11.toByte,
                                  12.toByte,
                                  13.toByte,
                                  14.toByte,
                                  15.toByte)

        convertHexStringToByteArray(sourceString) should be(expectedArray)
      }

      it("should return an empty array for an empty string") {
        val sourceString  = ""
        val expectedArray = Array.empty[Byte]

        convertHexStringToByteArray(sourceString) should be(expectedArray)
      }

      it("should throw a NumberFormatException for an invalid hex string") {
        a[NumberFormatException] should be thrownBy convertHexStringToByteArray(
          "This is no hex string!"
        )
      }
    }

    describe("getAgentsInSpecificState") {
      describe("if given an empty map") {
        it("should return an empty map") {
          getAgentsInSpecificState(Map.empty[String, AgentInformation], TenseiAgentState.Idle) should be(
            Map.empty[String, AgentInformation]
          )
        }
      }

      describe("if no agent has the desired state") {
        it("should return an empty map") {
          val workingState = AgentWorkingState("1",
                                               TenseiAgentState.CleaningUp,
                                               ParserState.Idle,
                                               ProcessorState.Idle,
                                               Map.empty[String, RuntimeStats])
          val agents = Map(
            "1" -> AgentInformation("1",
                                    "",
                                    AgentAuthorizationState.Connected,
                                    System.currentTimeMillis(),
                                    Option(workingState)),
            "2" -> AgentInformation(
              "2",
              "",
              AgentAuthorizationState.Connected,
              System.currentTimeMillis(),
              Option(workingState.copy(id = "2", state = TenseiAgentState.Working))
            ),
            "3" -> AgentInformation(
              "3",
              "",
              AgentAuthorizationState.Unauthorized,
              System.currentTimeMillis(),
              Option(workingState.copy(id = "3", state = TenseiAgentState.Idle))
            ),
            "4" -> AgentInformation("4",
                                    "",
                                    AgentAuthorizationState.Disconnected,
                                    System.currentTimeMillis(),
                                    None)
          )
          getAgentsInSpecificState(agents, TenseiAgentState.Aborting) should be(
            Map.empty[String, AgentInformation]
          )
        }
      }

      describe("if some agents have the desired state") {
        it("should return these agents") {
          val workingState = AgentWorkingState("1",
                                               TenseiAgentState.Working,
                                               ParserState.Idle,
                                               ProcessorState.Idle,
                                               Map.empty[String, RuntimeStats])
          val agents = Map(
            "1" -> AgentInformation("1",
                                    "",
                                    AgentAuthorizationState.Connected,
                                    System.currentTimeMillis(),
                                    Option(workingState)),
            "2" -> AgentInformation("2",
                                    "",
                                    AgentAuthorizationState.Connected,
                                    System.currentTimeMillis(),
                                    Option(workingState.copy(id = "2"))),
            "3" -> AgentInformation(
              "3",
              "",
              AgentAuthorizationState.Unauthorized,
              System.currentTimeMillis(),
              Option(workingState.copy(id = "3", state = TenseiAgentState.Idle))
            ),
            "4" -> AgentInformation("4",
                                    "",
                                    AgentAuthorizationState.Connected,
                                    System.currentTimeMillis(),
                                    Option(workingState.copy(id = "4")))
          )
          val expectedAgents = Map(
            "1" -> AgentInformation("1",
                                    "",
                                    AgentAuthorizationState.Connected,
                                    System.currentTimeMillis(),
                                    Option(workingState)),
            "2" -> AgentInformation("2",
                                    "",
                                    AgentAuthorizationState.Connected,
                                    System.currentTimeMillis(),
                                    Option(workingState.copy(id = "2"))),
            "4" -> AgentInformation("4",
                                    "",
                                    AgentAuthorizationState.Connected,
                                    System.currentTimeMillis(),
                                    Option(workingState.copy(id = "4")))
          )

          val result = getAgentsInSpecificState(agents, TenseiAgentState.Working)
          result.size should be(expectedAgents.size)
          result.keySet.foreach(key => expectedAgents.get(key).isDefined should be(right = true))
        }
      }
    }

    describe("validateEncodedLicense") {
      describe("if the input is empty") {
        it("should return invalid") {
          validateEncodedLicense("") should be(
            LicenseValidationResult.Invalid(Option(InvalidLicenseReason.Damaged))
          )
        }
      }

      describe("if the input is garbage") {
        it("should return invalid") {
          validateEncodedLicense(scala.util.Random.alphanumeric.take(64).mkString) should be(
            LicenseValidationResult.Invalid(Option(InvalidLicenseReason.Damaged))
          )
        }
      }
    }
  }
}
