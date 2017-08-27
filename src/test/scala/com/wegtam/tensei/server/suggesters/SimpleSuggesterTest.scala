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

import com.wegtam.tensei.adt.Recipe.MapOneToOne
import com.wegtam.tensei.adt._
import com.wegtam.tensei.server.DefaultSpec

import scalaz.{ Failure, NonEmptyList, Success }

class SimpleSuggesterTest extends DefaultSpec with SimpleSuggester {
  private def createDFASDLfromFile(id: String, path: String): DFASDL = {
    val in: InputStream = getClass.getResourceAsStream(path)
    val xml             = scala.io.Source.fromInputStream(in).mkString
    DFASDL(id = id, content = xml)
  }

  describe("suggest") {
    describe("should return a failure") {
      it("when given an empty cookbook") {
        val cookbook = Cookbook("EMPTY", List.empty[DFASDL], None, List.empty[Recipe])
        val result   = suggest(cookbook)
        result.isFailure should be(true)
        result should be(
          Failure(
            NonEmptyList(s"No sources or target DFASDL defined in Cookbook '${cookbook.id}'!")
          )
        )
      }

      it("when given a cookbook without sources") {
        val cookbook = Cookbook(
          "WITHOUT-SOURCES",
          List.empty[DFASDL],
          Option(
            createDFASDLfromFile("target", "/com/wegtam/tensei/server/suggesters/csv-simple.xml")
          ),
          List.empty[Recipe]
        )
        val result = suggest(cookbook)
        result.isFailure should be(true)
        result should be(
          Failure(
            NonEmptyList(s"No sources or target DFASDL defined in Cookbook '${cookbook.id}'!")
          )
        )
      }

      it("when given a cookbook without target") {
        val cookbook = Cookbook(
          "WITHOUT-TARGET",
          List(
            createDFASDLfromFile("source", "/com/wegtam/tensei/server/suggesters/csv-simple.xml")
          ),
          None,
          List.empty[Recipe]
        )
        val result = suggest(cookbook)
        result.isFailure should be(true)
        result should be(
          Failure(
            NonEmptyList(s"No sources or target DFASDL defined in Cookbook '${cookbook.id}'!")
          )
        )
      }

      it("when no recipes could be created") {
        val cookbook = Cookbook(
          "WITHOUT-TARGET",
          List(
            createDFASDLfromFile("source", "/com/wegtam/tensei/server/suggesters/csv-simple.xml")
          ),
          Option(
            createDFASDLfromFile("target", "/com/wegtam/tensei/server/suggesters/csv-simple-02.xml")
          ),
          List.empty[Recipe]
        )
        val result = suggest(cookbook)
        result.isFailure should be(true)
        result should be(
          Failure(NonEmptyList(s"No recipes could be created for cookbook '${cookbook.id}'."))
        )
      }
    }

    describe("when given the same source and target dfasdl") {
      val dfasdlTestFiles =
        List("csv-simple.xml", "csv-simple-with-elem.xml", "csv-with-seq.xml", "csv-with-head.xml")
      val expectedCookbooks: Map[String, Cookbook] = Map(
        "csv-simple.xml" -> Cookbook(
          "csv-simple.xml",
          List(
            createDFASDLfromFile("source", s"/com/wegtam/tensei/server/suggesters/csv-simple.xml")
          ),
          Option(
            createDFASDLfromFile("target", s"/com/wegtam/tensei/server/suggesters/csv-simple.xml")
          ),
          List(
            Recipe("auto-generated-1",
                   MapOneToOne,
                   List(
                     MappingTransformation(List(ElementReference("source", "name")),
                                           List(ElementReference("target", "name")))
                   )),
            Recipe("auto-generated-2",
                   MapOneToOne,
                   List(
                     MappingTransformation(List(ElementReference("source", "phone")),
                                           List(ElementReference("target", "phone")))
                   )),
            Recipe("auto-generated-3",
                   MapOneToOne,
                   List(
                     MappingTransformation(List(ElementReference("source", "email")),
                                           List(ElementReference("target", "email")))
                   ))
          )
        ),
        "csv-simple-with-elem.xml" -> Cookbook(
          "csv-simple-with-elem.xml",
          List(
            createDFASDLfromFile("source",
                                 s"/com/wegtam/tensei/server/suggesters/csv-simple-with-elem.xml")
          ),
          Option(
            createDFASDLfromFile("target",
                                 s"/com/wegtam/tensei/server/suggesters/csv-simple-with-elem.xml")
          ),
          List(
            Recipe("auto-generated-1",
                   MapOneToOne,
                   List(
                     MappingTransformation(List(ElementReference("source", "name")),
                                           List(ElementReference("target", "name")))
                   )),
            Recipe("auto-generated-2",
                   MapOneToOne,
                   List(
                     MappingTransformation(List(ElementReference("source", "phone")),
                                           List(ElementReference("target", "phone")))
                   )),
            Recipe("auto-generated-3",
                   MapOneToOne,
                   List(
                     MappingTransformation(List(ElementReference("source", "email")),
                                           List(ElementReference("target", "email")))
                   ))
          )
        ),
        "csv-with-seq.xml" -> Cookbook(
          "csv-with-seq.xml",
          List(
            createDFASDLfromFile("source", s"/com/wegtam/tensei/server/suggesters/csv-with-seq.xml")
          ),
          Option(
            createDFASDLfromFile("target", s"/com/wegtam/tensei/server/suggesters/csv-with-seq.xml")
          ),
          List(
            Recipe(
              "auto-generated-1",
              MapOneToOne,
              List(
                MappingTransformation(List(ElementReference("source", "name-value")),
                                      List(ElementReference("target", "name-value"))),
                MappingTransformation(List(ElementReference("source", "email-value")),
                                      List(ElementReference("target", "email-value"))),
                MappingTransformation(List(ElementReference("source", "phone-value")),
                                      List(ElementReference("target", "phone-value")))
              )
            )
          )
        ),
        "csv-with-head.xml" -> Cookbook(
          "csv-with-head.xml",
          List(
            createDFASDLfromFile("source", "/com/wegtam/tensei/server/suggesters/csv-with-head.xml")
          ),
          Option(
            createDFASDLfromFile("target", "/com/wegtam/tensei/server/suggesters/csv-with-head.xml")
          ),
          List(
            Recipe(
              "auto-generated-1",
              MapOneToOne,
              List(
                MappingTransformation(
                  List(
                    ElementReference("source", "telefon")
                  ),
                  List(
                    ElementReference("target", "telefon")
                  )
                ),
                MappingTransformation(
                  List(
                    ElementReference("source", "nachname")
                  ),
                  List(
                    ElementReference("target", "nachname")
                  )
                ),
                MappingTransformation(
                  List(
                    ElementReference("source", "vorname")
                  ),
                  List(
                    ElementReference("target", "vorname")
                  )
                ),
                MappingTransformation(
                  List(
                    ElementReference("source", "geburtsdatum")
                  ),
                  List(
                    ElementReference("target", "geburtsdatum")
                  )
                )
              )
            ),
            Recipe(
              "auto-generated-2",
              MapOneToOne,
              List(
                MappingTransformation(
                  List(
                    ElementReference("source", "csv_header_telefon")
                  ),
                  List(
                    ElementReference("target", "csv_header_telefon")
                  )
                ),
                MappingTransformation(
                  List(
                    ElementReference("source", "csv_header_vorname")
                  ),
                  List(
                    ElementReference("target", "csv_header_vorname")
                  )
                ),
                MappingTransformation(
                  List(
                    ElementReference("source", "csv_header_nachname")
                  ),
                  List(
                    ElementReference("target", "csv_header_nachname")
                  )
                ),
                MappingTransformation(
                  List(
                    ElementReference("source", "csv_header_geburtsdatum")
                  ),
                  List(
                    ElementReference("target", "csv_header_geburtsdatum")
                  )
                )
              )
            )
          )
        )
      )
      dfasdlTestFiles foreach { dfasdlName =>
        describe(s"using $dfasdlName") {
          it("should return a cookbook with the correct recipes") {
            val cookbook = Cookbook(
              s"$dfasdlName",
              List(
                createDFASDLfromFile("source", s"/com/wegtam/tensei/server/suggesters/$dfasdlName")
              ),
              Option(
                createDFASDLfromFile("target", s"/com/wegtam/tensei/server/suggesters/$dfasdlName")
              ),
              List.empty[Recipe]
            )
            val result = suggest(cookbook)
            result.isSuccess should be(true)
            result fold (f => fail("An unexpected error occurred!"), c => {
              (c.recipes zip expectedCookbooks(dfasdlName).recipes) foreach (
                  r => r._1 shouldEqual r._2
              )
            })
            result should be(Success(expectedCookbooks(dfasdlName)))
          }
        }
      }
    }
  }
}
