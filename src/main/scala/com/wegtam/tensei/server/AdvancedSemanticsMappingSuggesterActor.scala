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

import akka.actor.{ Actor, ActorLogging, Props }
import com.wegtam.tensei.adt.{ StatusMessage, StatusType }
import com.wegtam.tensei.server.suggesters.{
  AdvancedSemanticsSuggester,
  MappingSuggesterMessages,
  MappingSuggesterModes
}

import scalaz._, Scalaz._

object AdvancedSemanticsMappingSuggesterActor {
  def props(): Props = Props(classOf[AdvancedSemanticsMappingSuggesterActor])
}

/**
  * An actor that provides a simple semantic mapping suggester.
  */
class AdvancedSemanticsMappingSuggesterActor
    extends AdvancedSemanticsSuggester
    with Actor
    with ActorLogging {
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def receive: Receive = {
    case MappingSuggesterMessages.SuggestMapping(cookbook, mode, answerTo) =>
      log.debug("Got advanced semantics suggest mapping message.")
      val receiver = answerTo getOrElse sender()
      if (mode != MappingSuggesterModes.AdvancedSemantics) {
        val error = new StatusMessage(
          reporter = None,
          message = s"Suggestion mode '$mode' not supported by semantic mapping suggester!",
          statusType = StatusType.FatalError,
          cause = None
        )
        receiver ! MappingSuggesterMessages.MappingSuggesterErrorMessage(error = error)
      } else {
        val result = suggest(cookbook)
        result match {
          case Success(c) =>
            receiver ! MappingSuggesterMessages.SuggestedMapping(c, mode)
          case Failure(f) =>
            val cause = new StatusMessage(reporter = None,
                                          message = f.toList.mkString(", "),
                                          statusType = StatusType.FatalError,
                                          cause = None)
            val error = new StatusMessage(reporter = None,
                                          message = "Unable to suggest mapping because of errors!",
                                          statusType = StatusType.FatalError,
                                          cause = Option(cause))
            receiver ! MappingSuggesterMessages.MappingSuggesterErrorMessage(error = error)
        }
      }
      // We commit seppuku...
      context stop self
  }
}
