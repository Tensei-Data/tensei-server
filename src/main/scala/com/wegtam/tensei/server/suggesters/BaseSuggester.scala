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

import com.wegtam.tensei.adt.{ Cookbook, DFASDL, ElementReference }
import org.dfasdl.utils._
import org.w3c.dom.Element

import scalaz.ValidationNel

/**
  * A base trait for all suggesters that extends the needed `DocumentHelpers` trait from the dfasdl-utils package.
  */
trait BaseSuggester extends DocumentHelpers {

  /**
    * The suggest method does the actual suggestion work.
    * It returns a scalaz.Validation that holds either the error messages or the resulting cookbook.
    *
    * @param cookbook The base cookbook.
    * @return A scalaz.Validation that holds either the error messages or the resulting cookbook.
    */
  def suggest(cookbook: Cookbook): ValidationNel[String, Cookbook]

  /**
    * Informations about an ID e.g. about an element.
    *
    * @param ref       The element reference.
    * @param ancestors The List of the element's ancestor elements.
    */
  case class IdInformation(ref: ElementReference, ancestors: List[Element]) {
    def isInChoice: Boolean =
      ancestors.exists(p => getStructureElementType(p.getNodeName) == StructureElementType.Choice)

    def isInSequence: Boolean =
      ancestors.exists(
        p => StructureElementType.isSequence(getStructureElementType(p.getNodeName))
      )
  }

  /**
    * Try to go upwards the elment tree and return the parent elements.
    *
    * @param e       An xml dom element.
    * @param parents A list of parent elements.
    * @return A list if parent elements e.g. the ancestors.
    */
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf", "org.wartremover.warts.Null"))
  def getAncestorElements(e: Element, parents: List[Element]): List[Element] = {
    val p = e.getParentNode.asInstanceOf[Element]
    if (p == null || p.getNodeName == ElementNames.ROOT)
      parents
    else
      getAncestorElements(p, parents ::: p :: Nil)
  }

  /**
    * Try to go upwards the elment tree and return the parent elements.
    *
    * @param e An xml dom element.
    * @return A list if parent elements e.g. the ancestors.
    */
  def getAncestorElements(e: Element): List[Element] =
    getAncestorElements(e, List.empty)

  /**
    * Extract all ids and their meta informations from the given dfasdl.
    *
    * @param dfasdl A DFASDL.
    */
  def extractIdInformationsFromDfasdl(dfasdl: DFASDL): Map[String, IdInformation] =
    if (dfasdl.content.isEmpty)
      Map.empty[String, IdInformation]
    else {
      val tree = createNormalizedDocument(dfasdl.content)
      val idInformations = getSortedIdList(dfasdl.content)
        .filter(id => isDataElement(tree.getElementById(id).getNodeName)) map { id =>
        val e = tree.getElementById(id)
        require(e != null, s"Element with ID '$id' not found!")
        (id, new IdInformation(ElementReference(dfasdl.id, id), getAncestorElements(e)))
      }
      idInformations.toMap
    }

  /**
    * Extract all semantic values and their meta informations from the given dfasdl.
    *
    * @param dfasdl A DFASDL.
    */
  def extractSemanticInformationsFromDfasdl(dfasdl: DFASDL): Map[String, IdInformation] =
    if (dfasdl.content.isEmpty)
      Map.empty[String, IdInformation]
    else {
      val tree = createNormalizedDocument(dfasdl.content)
      val idInformations = getSortedIdList(dfasdl.content).filter(id => {
        val e = tree.getElementById(id)
        isDataElement(e.getNodeName) && e.hasAttribute(AttributeNames.SEMANTIC)
      }) map { id =>
        val e = tree.getElementById(id)
        require(e != null, s"Element with ID '$id' not found!")
        (e.getAttribute(AttributeNames.SEMANTIC),
         new IdInformation(ElementReference(dfasdl.id, id), getAncestorElements(e)))
      }
      idInformations.toMap
    }
}
