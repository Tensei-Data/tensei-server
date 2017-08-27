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
import org.dfasdl.utils.StructureElementType

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scalaz._, Scalaz._

/**
  * This trait implements a semantic mapping suggester that considers the sematic of the elements.
  */
trait SemanticSuggester extends BaseSuggester {

  /**
    * The suggest method does the actual suggestion work.
    * It returns a scalaz.Validation that holds either the error messages or the resulting cookbook.
    *
    * @param cookbook The base cookbook.
    * @return A scalaz.Validation that holds either the error messages or the resulting cookbook.
    */
  override def suggest(cookbook: Cookbook): ValidationNel[String, Cookbook] =
    if (cookbook.sources.isEmpty || cookbook.target.isEmpty)
      s"No sources or target DFASDL defined in Cookbook '${cookbook.id}'!".failNel[Cookbook]
    else {
      val sources       = cookbook.sources.map(dfasdl => extractSemanticInformationsFromDfasdl(dfasdl))
      val target        = extractSemanticInformationsFromDfasdl(cookbook.target.get)
      var recipeCounter = 0L
      val recipes       = new ListBuffer[mutable.HashMap[String, Recipe]]()
      sources.foreach(info => {
        val recipeBuffer = new mutable.HashMap[String, Recipe]()
        recipes += recipeBuffer
        val mappingCandidates: List[(String, IdInformation)] =
          info.filter(ref => target.get(ref._1).isDefined).toList
        mappingCandidates foreach {
          sourceSemantic =>
            val semantic   = sourceSemantic._2.ref.elementId
            val sourceInfo = sourceSemantic._2
            val targetInfo = target.find(e => e._1 == sourceSemantic._1)
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
                recipeBuffer += (parentSequence.get.getAttribute("id") -> recipe)
              }
            } else {
              recipeCounter += 1
              val recipe = Recipe(
                s"auto-generated-$recipeCounter",
                MapOneToOne,
                List(MappingTransformation(List(sourceInfo.ref), List(targetInfo.get._2.ref)))
              )
              recipeBuffer += (semantic -> recipe)
            }
        }
      })
      if (recipeCounter > 0)
        cookbook.copy(recipes = recipes.flatMap(_.values).toList.sortBy(_.id)).successNel[String]
      else
        s"No recipes could be created for cookbook '${cookbook.id}'.".failNel[Cookbook]
    }
}
