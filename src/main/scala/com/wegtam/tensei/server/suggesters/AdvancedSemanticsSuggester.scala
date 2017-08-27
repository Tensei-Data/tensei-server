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
  * This trait implements a mapping suggester that considers the sematic and simple information of the elements.
  */
trait AdvancedSemanticsSuggester extends BaseSuggester {

  /**
    * The suggest method does the actual suggestion work.
    * It returns a scalaz.Validation that holds either the error messages or the resulting cookbook.
    *
    * @param cookbook The base cookbook.
    * @return A scalaz.Validation that holds either the error messages or the resulting cookbook.
    */
  override def suggest(cookbook: Cookbook): ValidationNel[String, Cookbook] = {
    if (cookbook.sources.isEmpty || cookbook.target.isEmpty)
      s"No sources or target DFASDL defined in Cookbook '${cookbook.id}'!".failNel[Cookbook]
    else {
      val sourcesSemantics =
        cookbook.sources.map(dfasdl => extractSemanticInformationsFromDfasdl(dfasdl))
      val targetSemantics   = extractSemanticInformationsFromDfasdl(cookbook.target.get)
      val sourceSemanticIds = new ListBuffer[String]
      val targetSemanticIds = new ListBuffer[String]

      var recipeCounter = 0L
      var recipes       = new ListBuffer[mutable.HashMap[String, Recipe]]()
      sourcesSemantics.foreach(info => {
        val recipeBuffer = new mutable.HashMap[String, Recipe]()
        recipes += recipeBuffer
        val mappingCandidates: List[(String, IdInformation)] =
          info.filter(ref => targetSemantics.get(ref._1).isDefined).toList

        mappingCandidates foreach {
          sourceSemantic =>
            val semantic   = sourceSemantic._2.ref.elementId
            val sourceInfo = sourceSemantic._2
            val targetInfo = targetSemantics.find(e => e._1 == sourceSemantic._1)
            require(targetInfo.isDefined,
                    s"No target element information found for source element $sourceInfo!")
            if (sourceInfo.isInChoice || sourceInfo.isInSequence || targetInfo.get._2.isInChoice || targetInfo.get._2.isInSequence) {
              if (sourceInfo.isInSequence && targetInfo.get._2.isInSequence) {
                val parentSequence = sourceInfo.ancestors.find(
                  e => StructureElementType.isSequence(getStructureElementType(e.getNodeName))
                )
                val recipe =
                  if (recipeBuffer.get(parentSequence.get.getAttribute("id")).isDefined) {
                    val r = recipeBuffer(parentSequence.get.getAttribute("id"))
                    r.copy(
                      mappings = r.mappings ::: MappingTransformation(
                        List(sourceInfo.ref),
                        List(targetInfo.get._2.ref)
                      ) :: Nil
                    )
                  } else {
                    recipeCounter += 1
                    Recipe(
                      s"auto-generated-$recipeCounter",
                      MapOneToOne,
                      List(
                        MappingTransformation(List(sourceInfo.ref), List(targetInfo.get._2.ref))
                      )
                    )
                  }
                sourceSemanticIds += sourceInfo.ref.elementId
                targetSemanticIds += targetInfo.get._2.ref.elementId
                recipeBuffer += (parentSequence.get.getAttribute("id") -> recipe)
              }
            } else {
              recipeCounter += 1
              val recipe = Recipe(
                s"auto-generated-$recipeCounter",
                MapOneToOne,
                List(MappingTransformation(List(sourceInfo.ref), List(targetInfo.get._2.ref)))
              )
              sourceSemanticIds += sourceInfo.ref.elementId
              targetSemanticIds += targetInfo.get._2.ref.elementId
              recipeBuffer += (semantic -> recipe)
            }
        }
      })

      var sourcesSimple = cookbook.sources.map(dfasdl => extractIdInformationsFromDfasdl(dfasdl))
      var targetSimple  = extractIdInformationsFromDfasdl(cookbook.target.get)
      sourcesSimple = sourcesSimple map { entry =>
        entry.filterNot(e => sourceSemanticIds.contains(e._1))
      }
      targetSimple = targetSimple.filterNot(e => targetSemanticIds.contains(e._1))

      sourcesSimple.foreach(info => {
        val recipeBuffer = new mutable.HashMap[String, Recipe]()
        val mappingCandidates: List[IdInformation] =
          info.filter(ref => targetSimple.get(ref._1).isDefined).values.toList
        mappingCandidates foreach {
          eInfo =>
            val id = eInfo.ref.elementId
            if (info(id).isInChoice || info(id).isInSequence || targetSimple(id).isInChoice || targetSimple(
                  id
                ).isInSequence) {
              if (info(id).isInSequence && targetSimple(id).isInSequence) {
                val parentSequence = info(id).ancestors.find(
                  e => StructureElementType.isSequence(getStructureElementType(e.getNodeName))
                )
                val idParentSequence       = parentSequence.get.getAttribute("id")
                val elementInRecipesIndex  = recipes.indexWhere(_.contains(idParentSequence))
                val recipe: Option[Recipe] =
                  // Check whether there alreaday exists a recipe with the parent ID from the semantic analysis
                  if (elementInRecipesIndex >= 0) {
                    val recipesElement: mutable.HashMap[String, Recipe] =
                      recipes(elementInRecipesIndex)
                    val newEntry = recipesElement
                      .get(idParentSequence)
                      .get
                      .copy(
                        mappings = recipesElement
                          .get(idParentSequence)
                          .get
                          .mappings ::: MappingTransformation(
                          List(eInfo.ref),
                          List(eInfo.ref.copy(dfasdlId = cookbook.target.get.id))
                        ) :: Nil
                      )
                    recipesElement.update(idParentSequence, newEntry)
                    recipes.update(elementInRecipesIndex, recipesElement)
                    None
                  } else if (recipeBuffer.get(idParentSequence).isDefined) {
                    val r = recipeBuffer(idParentSequence)
                    Option(
                      r.copy(
                        mappings = r.mappings ::: MappingTransformation(
                          List(eInfo.ref),
                          List(eInfo.ref.copy(dfasdlId = cookbook.target.get.id))
                        ) :: Nil
                      )
                    )
                  } else {
                    recipeCounter += 1
                    Option(
                      Recipe(s"auto-generated-$recipeCounter",
                             MapOneToOne,
                             List(
                               MappingTransformation(
                                 List(eInfo.ref),
                                 List(eInfo.ref.copy(dfasdlId = cookbook.target.get.id))
                               )
                             ))
                    )
                  }
                if (recipe.isDefined) {
                  recipeBuffer += (idParentSequence -> recipe.get)
                }
              }
            } else {
              // Is the current element in a surrounding structure? If so, we
              // extract the ID of the parent element
              val parentId =
                if (eInfo.ancestors.nonEmpty && eInfo.ancestors.head.hasAttribute("id")
                    && !eInfo.ancestors.head.getNodeName.equals(ElementNames.ROOT))
                  Option(eInfo.ancestors.head.getAttribute("id"))
                else
                  None

              // we have a surrounding parent element
              if (parentId.isDefined) {
                // do we already have a recipe for this surrounding structure?
                val recipe =
                  if (recipeBuffer.contains(parentId.get)) {
                    val targetRecipe = recipeBuffer(parentId.get)
                    targetRecipe.copy(
                      mappings = targetRecipe.mappings ::: MappingTransformation(
                        List(eInfo.ref),
                        List(eInfo.ref.copy(dfasdlId = cookbook.target.get.id))
                      ) :: Nil
                    )
                  } else {
                    recipeCounter += 1
                    Recipe(s"auto-generated-$recipeCounter",
                           MapOneToOne,
                           List(
                             MappingTransformation(
                               List(eInfo.ref),
                               List(eInfo.ref.copy(dfasdlId = cookbook.target.get.id))
                             )
                           ))
                  }
                recipeBuffer += (parentId.get -> recipe)
              } else {
                recipeCounter += 1
                val recipe = Recipe(
                  s"auto-generated-$recipeCounter",
                  MapOneToOne,
                  List(
                    MappingTransformation(List(eInfo.ref),
                                          List(eInfo.ref.copy(dfasdlId = cookbook.target.get.id)))
                  )
                )
                recipeBuffer += (id -> recipe)
              }
            }
        }
        recipes += recipeBuffer
      })

      if (recipeCounter > 0)
        cookbook.copy(recipes = recipes.flatMap(_.values).toList.sortBy(_.id)).successNel[String]
      else
        s"No recipes could be created for cookbook '${cookbook.id}'.".failNel[Cookbook]
    }
  }
}
