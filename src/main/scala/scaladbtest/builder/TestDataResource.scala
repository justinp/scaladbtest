package scaladbtest.builder

import util.parsing.combinator.JavaTokenParsers
import scaladbtest.model.value.Value
import io.Source
import scaladbtest.model._
import java.io.InputStream

/*
* Copyright 2010 Ken Egervari
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

object TestDataResource {

	def removeQuotes(stringLiteral: String) = {
		stringLiteral.substring(1, stringLiteral.length - 1)
	}

  def removeTripleQuotes(stringLiteral: String) = {
		stringLiteral.substring(3, stringLiteral.length - 3)
	}

}

class TestDataResource(val testData: TestData) extends JavaTokenParsers {

	import TestDataResource._

	def tables: Parser[Any] = rep(table)

	def table: Parser[Table] = ident ~ ":" ~ opt(defaultColumns) ~ rep(record) ^^ {
		case t ~ ":" ~ Some(defaultColumns) ~ records => {
			testData.createTable(t, defaultColumns, records)
		}

		case t ~ ":" ~ None ~ records => {
			testData.createTable(t, List(), records)
		}
	}

	def defaultColumns: Parser[List[DefaultColumn]] = "?" ~ repsep(defaultColumn, ",") ^^ {
		case "?" ~ columns => columns  
	}

	def defaultColumn: Parser[DefaultColumn] = ident ~ ":" ~ value ^^ {
		case n ~ ":" ~ v => DefaultColumn(n.toString, Value.parse(v))
	}

	def record: Parser[Record] = "-" ~ opt(recordLabel) ~ repsep(column, ",") ^^ {
		case "-" ~ label ~ columns => new Record(label, columns)
	}

	def recordLabel: Parser[String] =
		"[" ~ ident ~ "]" ^^ { case "[" ~ label ~ "]" => label }

	def column: Parser[Column] = ident ~ ":" ~ value ^^ { 
		case n ~ ":" ~ v => Column(n.toString, Value.parse(v))
	}

  private val tripleQuotedStringLiteral =
      ("\"\"\""+"""(\n|[^\p{Cntrl}\\]|\\[\\/bfnrt]|\\u[a-fA-F0-9]{4})*?"""+"\"\"\"").r

	def value: Parser[String] =
    tripleQuotedStringLiteral ^^ (removeTripleQuotes(_)) |
		stringLiteral ^^ (removeQuotes(_)) |
		floatingPointNumber |
		"true" ^^ (s => "$true") |
		"$true" |
		"false" ^^ (s => "$false") |
		"$false" |
		"$label" |
		"$now" |
		"$null" |
		"null" ^^ ("$" + _)

	def loadFrom(filename: String):ParseResult[Any] = loadFrom(Source.fromFile(filename),filename)

	def loadFrom(in: InputStream,name: String):ParseResult[Any] = loadFrom(Source.fromInputStream(in),name)

	def loadFrom(source: Source,name: String):ParseResult[Any] = {
		val filtered = source.getLines.filterNot(_.startsWith("#")).mkString("\n")
                val parseResult = parseAll(tables, filtered)
                if(!parseResult.successful) {
                        throw new TestDataParseException(parseResult.toString)
                }
		parseResult
        }
}
