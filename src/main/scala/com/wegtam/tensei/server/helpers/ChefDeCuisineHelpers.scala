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

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths }
import java.security.interfaces.{ RSAPrivateKey, RSAPublicKey }
import java.util.Base64

import argonaut._
import com.wegtam.tensei.adt._
import com.wegtam.tensei.agent.TenseiAgentState
import com.wegtam.tensei.security.CryptoHelpers

import scala.util.Try
import scalaz._

/**
  * Some helper functions for the chef de cuisine.
  */
@SuppressWarnings(Array("org.wartremover.warts.Throw"))
@throws[IllegalArgumentException](cause = "The master key file has an invalid format!")
trait ChefDeCuisineHelpers extends CryptoHelpers {
  private final val SEED_BYTES: Array[Byte] = convertHexStringToByteArray(
    "80220B06B0AC89F4B0A649F7CA137570"
  )
  // Location of the license key for free use which should be bundled with the application.
  protected final val FREE_LICENSE: String = System.getProperty("user.dir") + System.getProperty(
    "file.separator"
  ) + "free.license"
  // Location of the license key for commercial use which can be purchased.
  protected final val LICENSE: String = System.getProperty("user.dir") + System.getProperty(
    "file.separator"
  ) + "tensei.license"

  // Return the parts of the master key.
  private val (masterKeyMod, masterKeyExp) = {
    val keyBytes = loadKey("/com/wegtam/tensei/server/helpers/master_key")
    val parts    = new String(Base64.getDecoder.decode(keyBytes)).split(";")
    if (parts.size != 2)
      throw new IllegalArgumentException("Illegal file format for master key file!")
    else {
      val mod = new BigInteger(parts(0))
      val exp = new BigInteger(parts(1))
      (mod.toByteArray, exp.toByteArray)
    }
  }

  // Return the parts of our key.
  private val (serverKeyMod, serverKeyExp) = {
    val keyBytes = loadKey("/com/wegtam/tensei/server/helpers/server_key")
    val parts    = new String(Base64.getDecoder.decode(keyBytes)).split(";")
    if (parts.size != 2)
      throw new IllegalArgumentException("Illegal file format for server key file!")
    else {
      val mod = new BigInteger(parts(0))
      val exp = new BigInteger(parts(1))
      (mod.toByteArray, exp.toByteArray)
    }
  }

  /**
    * Set the specified number of agents to authorized state in the given data map.
    *
    * @param agents         A map of agent informations mapped to their id.
    * @param numberOfAgents The number of agents that should be authorized.
    * @return An updated map of agent informations.
    */
  def authorizeAgents(agents: Map[String, AgentInformation],
                      numberOfAgents: Int): Map[String, AgentInformation] =
    if (numberOfAgents >= agents.size) {
      // We simply need to authorize all agents.
      val authorizedAgents = agents
        .filter(_._2.auth == AgentAuthorizationState.Unauthorized)
        .map(e => e._1 -> e._2.copy(auth = AgentAuthorizationState.Connected))
      agents ++ authorizedAgents
    } else {
      val numberOfAuthorizedAgents =
        agents.count(_._2.auth != AgentAuthorizationState.Unauthorized)
      if (numberOfAuthorizedAgents == numberOfAgents)
        agents // We don't need to modify the agents.
      else if (numberOfAuthorizedAgents > numberOfAgents) {
        // We need to unauthorize some agents.
        val unauthorizeCounter = numberOfAuthorizedAgents - numberOfAgents
        val unauthorizedAgents = agents
          .filter(_._2.auth != AgentAuthorizationState.Unauthorized)
          .take(unauthorizeCounter)
          .map(e => e._1 -> e._2.copy(auth = AgentAuthorizationState.Unauthorized))
        agents ++ unauthorizedAgents
      } else {
        // We need to authorize some agents.
        val authorizeCounter = numberOfAgents - numberOfAuthorizedAgents
        val authorizedAgents = agents
          .filter(_._2.auth == AgentAuthorizationState.Unauthorized)
          .take(authorizeCounter)
          .map(e => e._1 -> e._2.copy(auth = AgentAuthorizationState.Connected))
        agents ++ authorizedAgents
      }
    }

  /**
    * Return all authorized agents from a given map.
    *
    * @param agents A map of agents mapped by their id.
    * @return All agents that are authorized.
    */
  def getAuthorizedAgents(agents: Map[String, AgentInformation]): Map[String, AgentInformation] =
    agents.filter(_._2.auth != AgentAuthorizationState.Unauthorized)

  /**
    * Return all connected agents from a given map.
    *
    * @param agents A map of agents mapped by their id.
    * @return All agents that are in connected state.
    */
  def getConnectedAgents(agents: Map[String, AgentInformation]): Map[String, AgentInformation] =
    agents.filter(_._2.auth == AgentAuthorizationState.Connected)

  /**
    * Filter the given map of agents and return the ones that are in a specific state.
    *
    * @param agents A map of agents mapped by their id.
    * @param state  The state the agent should be in.
    * @return All agents that have the desired state which can be an empty list.
    */
  def getAgentsInSpecificState(agents: Map[String, AgentInformation],
                               state: TenseiAgentState): Map[String, AgentInformation] =
    agents.filter(e => {
      val workingState = e._2.workingState
      workingState.map(_.state == state).getOrElse(false)
    })

  /**
    * Tries to read and decrypt the key from the given key file name.
    * If the key file can't be decrpyted a `RuntimeException` is thrown.
    *
    * @param file The name of the file that holds the key.
    * @return An array of bytes from the key file.
    */
  private def loadKey(file: String): Array[Byte] = {
    val key        = generateAESKeyFromParameters(SEED_BYTES)
    val fileSource = scala.io.Source.fromInputStream(getClass.getResourceAsStream(file)).mkString
    val content    = Base64.getDecoder.decode(fileSource).splitAt(DEFAULT_AES_KEY_LENGTH / 8)
    decrypt(content._2, getAESCipher, key, Option(content._1)) match {
      case -\/(failure) => throw failure
      case \/-(success) => success
    }
  }

  /**
    * Convert a given string containing hex values into an array of bytes.
    *
    * @param hexString A string holding just hexadecimal values without any separators of presets like "0x".
    * @return An array of bytes representing the given hex values.
    */
  def convertHexStringToByteArray(hexString: String): Array[Byte] =
    hexString.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)

  /**
    * Decrypt the given base 64 encoded license string and return a tuple that
    * holds a license validation result and an option the possibly decrypted license.
    *
    * @param base64encodedLicenseFile A string holding an encoded and signed license in base 64 format.
    * @return A tuple with a license validation result and an option to the decrypted license.
    */
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def decryptBase64EncodedLicense(
      base64encodedLicenseFile: String
  ): (LicenseValidationResult, Option[TenseiLicense]) =
    Try {
      val licenseFile = Base64.getDecoder.decode(base64encodedLicenseFile)

      val serverKey =
        generateRSAKeyFromParameters(serverKeyMod, serverKeyExp, classOf[RSAPrivateKey])
      val transmissionParts = licenseFile.splitAt(344)
      decrypt(Base64.getDecoder.decode(transmissionParts._1), getRSACipher, serverKey) match {
        case -\/(_) =>
          (LicenseValidationResult.Invalid(Option(InvalidLicenseReason.Damaged)), None)
        case \/-(decodedTransmissionParts) =>
          val (key, iv)       = decodedTransmissionParts.splitAt(DEFAULT_AES_KEY_LENGTH / 8)
          val transmissionKey = generateAESKeyFromParameters(key)
          decrypt(Base64.getDecoder.decode(transmissionParts._2),
                  getAESCipher,
                  transmissionKey,
                  Option(iv)) match {
            case -\/(_) =>
              (LicenseValidationResult.Invalid(Option(InvalidLicenseReason.Damaged)), None)
            case \/-(decodedLicense) =>
              val licenseParts = new String(decodedLicense).split("\n----- SIGNATURE -----\n")
              if (licenseParts.length != 2 || (licenseParts.length >= 2 && licenseParts(1).isEmpty))
                (LicenseValidationResult.Invalid(Option(InvalidLicenseReason.Unsigned)), None)
              else {
                val masterKey = generateRSAKeyFromParameters(
                  masterKeyMod,
                  masterKeyExp,
                  classOf[RSAPublicKey]
                ).asInstanceOf[RSAPublicKey]
                val signature = Base64.getDecoder.decode(licenseParts(1))
                if (!validate(licenseParts(0).getBytes(StandardCharsets.UTF_8),
                              signature,
                              masterKey))
                  (LicenseValidationResult.Invalid(Option(InvalidLicenseReason.InvalidSignature)),
                   None)
                else {
                  val license = Parse.decodeOption[TenseiLicense](licenseParts(0))
                  license.fold[(LicenseValidationResult, Option[TenseiLicense])](
                    (LicenseValidationResult.Invalid(Option(InvalidLicenseReason.Damaged)), None)
                  ) { l =>
                    if (l.expiresIn.isNegative)
                      (LicenseValidationResult.Invalid(Option(InvalidLicenseReason.Expired)),
                       license)
                    else
                      (LicenseValidationResult.Valid, license)
                  }
                }
              }
          }
      }
    } match {
      case scala.util.Failure(_) =>
        (LicenseValidationResult.Invalid(Option(InvalidLicenseReason.Damaged)), None)
      case scala.util.Success(s) => s
    }

  /**
    * Load the file from the given path and return a string with the
    * content. The file must be encoded with utf8.
    *
    * @param p The path to the file.
    * @return Either an error or the content.
    */
  def loadFileContent(p: Path): Try[String] =
    Try(scala.io.Source.fromFile(p.toFile, "UTF-8").mkString)

  /**
    * Overwrite the license file with the given license data.
    *
    * @param base64encodedLicenseFile A string holding an encoded and signed license in base 64 format.
    * @return Either an error or the path to the written file.
    */
  def updateLicenseFile(base64encodedLicenseFile: String): Try[Path] =
    Try(Files.write(Paths.get(LICENSE), base64encodedLicenseFile.getBytes(StandardCharsets.UTF_8)))

  /**
    * Validate the given encoded license file and return the result.
    *
    * @param base64encodedLicenseFile A string holding an encoded and signed license in base 64 format.
    * @return The license validation result.
    */
  def validateEncodedLicense(base64encodedLicenseFile: String): LicenseValidationResult =
    decryptBase64EncodedLicense(base64encodedLicenseFile)._1
}
