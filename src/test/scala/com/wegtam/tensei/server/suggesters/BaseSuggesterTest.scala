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

import java.io.InputStream

import com.wegtam.tensei.adt.{ Cookbook, DFASDL }
import com.wegtam.tensei.server.DefaultSpec
import org.dfasdl.utils.ElementNames
import org.w3c.dom.Element

import scala.collection.mutable.ListBuffer
import scalaz.ValidationNel

class BaseSuggesterTest extends DefaultSpec with BaseSuggester {

  /**
    * The suggest method does the actual suggestion work.
    * It returns a scalaz.Validation that holds either the error messages or the resulting cookbook.
    *
    * @param cookbook The base cookbook.
    * @return A scalaz.Validation that holds either the error messages or the resulting cookbook.
    */
  override def suggest(cookbook: Cookbook): ValidationNel[String, Cookbook] =
    fail("This method should never be called within a test!")

  describe("getAncestorElements") {
    val documentBuilder = createDocumentBuilder(useSchema = true)

    describe("when given an element without ancestors") {
      it("should return an empty list") {
        val doc = documentBuilder.newDocument()
        val e   = doc.createElement(ElementNames.ELEMENT)
        getAncestorElements(e) shouldEqual List.empty[Element]
      }
    }

    describe("when given an element directly beneath the root element") {
      it("should return an empty list") {
        val doc = documentBuilder.newDocument()
        doc.appendChild(doc.createElement(ElementNames.ROOT))
        val child = doc.createElement(ElementNames.ELEMENT)
        doc.getDocumentElement.appendChild(child)
        getAncestorElements(child) shouldEqual List.empty[Element]
      }
    }

    describe("when given an element with ancestors") {
      it("should return a list of the ancestor elements") {
        val parents                                = 10
        val expectedAncestors: ListBuffer[Element] = new ListBuffer[Element]()
        val doc                                    = documentBuilder.newDocument()
        doc.appendChild(doc.createElement(ElementNames.ROOT))
        val child = doc.createElement(ElementNames.ELEMENT)
        child.setAttribute("id", "child")
        var e = doc.getDocumentElement
        for (x <- 1 to parents) {
          val c = doc.createElement(ElementNames.ELEMENT)
          c.setAttribute("id", s"$x")
          e.appendChild(c)
          expectedAncestors += c
          e = c
        }
        e.appendChild(child)

        val ancestors = getAncestorElements(child)
        ancestors.size should be(parents)
        ancestors shouldEqual expectedAncestors.toList.reverse
      }
    }
  }

  describe("extractIdInformationsFromDfasdl") {
    describe("when given an empty dfasdl") {
      it("should return an empty map") {
        extractIdInformationsFromDfasdl(DFASDL(id = "TEST", content = "")) shouldEqual Map
          .empty[String, IdInformation]
      }
    }

    describe("when given a dfasdl") {
      it("should return the proper informations") {
        val in: InputStream =
          getClass.getResourceAsStream("/com/wegtam/tensei/server/suggesters/source-dfasdl.xml")
        val dfasdlString = scala.io.Source.fromInputStream(in).mkString
        val idInfos      = extractIdInformationsFromDfasdl(DFASDL(id = "TEST", content = dfasdlString))
        idInfos("body").ancestors.size should be(0)
        idInfos("toValue").ancestors.size should be(3)
        idInfos("ccValue").isInChoice should be(true)
        idInfos("subjectValue").isInSequence should be(true)
      }
    }
  }
}
