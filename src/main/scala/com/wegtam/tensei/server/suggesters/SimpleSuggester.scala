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

package com.wegtam.tensei.server.suggesters

import com.wegtam.tensei.adt.Recipe.MapOneToOne
import com.wegtam.tensei.adt.{ Cookbook, MappingTransformation, Recipe }
import org.dfasdl.utils.{ ElementNames, StructureElementType }

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scalaz._, Scalaz._

/**
  * This trait implements a simple mapping suggester.
  */
trait SimpleSuggester extends BaseSuggester {

  /**
    * The suggest method does the actual suggestion work.
    * It returns a scalaz.Validation that holds either the error messages or the resulting cookbook.
    *
    * @param cookbook The base cookbook.
    * @return A scalaz.Validation that holds either the error messages or the resulting cookbook.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  override def suggest(cookbook: Cookbook): ValidationNel[String, Cookbook] =
    if (cookbook.sources.isEmpty || cookbook.target.isEmpty)
      s"No sources or target DFASDL defined in Cookbook '${cookbook.id}'!".failNel[Cookbook]
    else {
      require(cookbook.target.isDefined, "No target ID defined in cookbook!")
      val cookbookTargetId = cookbook.target.map(_.id).getOrElse("UNDEFINED-TARGET-DFASDL")
      val sources          = cookbook.sources.map(dfasdl => extractIdInformationsFromDfasdl(dfasdl))
      val target           = cookbook.target.map(t => extractIdInformationsFromDfasdl(t)).getOrElse(Map.empty)
      var recipeCounter    = 0L
      val recipes          = new ListBuffer[mutable.HashMap[String, Recipe]]()
      sources.foreach(
        info => {
          val recipeBuffer = new mutable.HashMap[String, Recipe]()
          recipes.append(recipeBuffer)
          val mappingCandidates: List[IdInformation] =
            info.filter(ref => target.get(ref._1).isDefined).values.toList
          mappingCandidates.foreach {
            eInfo =>
              val id = eInfo.ref.elementId
              if (info(id).isInChoice || info(id).isInSequence || target(id).isInChoice || target(
                    id
                  ).isInSequence) {
                if (info(id).isInSequence && target(id).isInSequence) {
                  info(id).ancestors
                    .find(
                      e => StructureElementType.isSequence(getStructureElementType(e.getNodeName))
                    )
                    .foreach {
                      parentSequence =>
                        val recipe =
                          if (recipeBuffer.get(parentSequence.getAttribute("id")).isDefined) {
                            val r = recipeBuffer(parentSequence.getAttribute("id"))
                            r.copy(
                              mappings = r.mappings ::: MappingTransformation(
                                List(eInfo.ref),
                                List(eInfo.ref.copy(dfasdlId = cookbookTargetId))
                              ) :: Nil
                            )
                          } else {
                            recipeCounter += 1
                            Recipe(s"auto-generated-$recipeCounter",
                                   MapOneToOne,
                                   List(
                                     MappingTransformation(
                                       List(eInfo.ref),
                                       List(eInfo.ref.copy(dfasdlId = cookbookTargetId))
                                     )
                                   ))
                          }
                        val _ = recipeBuffer += (parentSequence.getAttribute("id") -> recipe)
                    }
                }
              } else {
                // Is the current element in a surrounding structure? If so, we
                // extract the ID of the parent element
                val parentIdO =
                  eInfo.ancestors.headOption.flatMap { h =>
                    if (h.getNodeName != ElementNames.ROOT && h.hasAttribute("id"))
                      Option(h.getAttribute("id"))
                    else
                      None
                  }
                parentIdO match {
                  case None =>
                    recipeCounter += 1
                    val recipe = Recipe(
                      s"auto-generated-$recipeCounter",
                      MapOneToOne,
                      List(
                        MappingTransformation(List(eInfo.ref),
                                              List(eInfo.ref.copy(dfasdlId = cookbookTargetId)))
                      )
                    )
                    val _ = recipeBuffer.put(id, recipe)
                  case Some(parentId) =>
                    // do we already have a recipe for this surrounding structure?
                    val recipe =
                      if (recipeBuffer.contains(parentId)) {
                        val targetRecipe = recipeBuffer(parentId)
                        targetRecipe.copy(
                          mappings = targetRecipe.mappings ::: MappingTransformation(
                            List(eInfo.ref),
                            List(eInfo.ref.copy(dfasdlId = cookbookTargetId))
                          ) :: Nil
                        )
                      } else {
                        recipeCounter += 1
                        Recipe(s"auto-generated-$recipeCounter",
                               MapOneToOne,
                               List(
                                 MappingTransformation(
                                   List(eInfo.ref),
                                   List(eInfo.ref.copy(dfasdlId = cookbookTargetId))
                                 )
                               ))
                      }
                    val _ = recipeBuffer.put(parentId, recipe)
                }
              }
          }
        }
      )
      if (recipeCounter > 0)
        cookbook.copy(recipes = recipes.flatMap(_.values).toList.sortBy(_.id)).successNel[String]
      else
        s"No recipes could be created for cookbook '${cookbook.id}'.".failNel[Cookbook]
    }
}
