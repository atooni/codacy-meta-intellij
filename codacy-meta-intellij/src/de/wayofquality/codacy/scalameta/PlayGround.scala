package de.wayofquality.codacy.scalameta

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files

import codacy.base.Pattern
import codacy.macros.Patterns
import codacy.utils.FileHelpers
import com.codacy.plugins.api.ErrorMessage
import com.codacy.plugins.api.results.{Pattern, Result}
import play.api.libs.json._

import scala.meta._
import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex

object PlayGround {

  private implicit val regexReader: Reads[Regex] = Reads(
    _.validate[String].flatMap { raw =>
      Try(raw.r) match {
        case Success(regex) => JsSuccess(regex)
        case Failure(ex) => JsError(ex.getMessage)
      }
    }
  )

  implicit val rWrites: Writes[Regex] = Writes((r:Regex) => Json.toJson(r.toString))

  private val f : File = new File("/home/andreas/projects/blended/core/blended.streams.dispatcher/src/main/scala/blended/streams/dispatcher/internal/builder/DispatcherBuilder.scala")

  private val newPatterns : Map[Pattern.Id, Pattern] = {


    Patterns.fromResources.mapValues { f =>
      val pattern = f.fromConfiguration(f.defaultConfig)
      //println(Json.toJson(f.defaultConfig))

      pattern
    }
  }

  private def patternsJson(classLoader: URLClassLoader): String  = {

    val files = FileHelpers.allPatternJsons(classLoader)
    lazy val params :Map[Pattern.Id,List[JsObject]] = Patterns.params

    val patterns = files.flatMap{ file =>
      Json.parse(file.contentAsString).\( "patterns" ).as[Set[JsObject]]
    }.map{ jPattern =>
      val id = (jPattern \ "patternId").as[Pattern.Id]

      val patternParams = params.get(id).filter(_.nonEmpty)

      id -> patternParams.map{ params =>
        jPattern ++ Json.obj("parameters" -> params)
      }.getOrElse(jPattern)

    }.toList.sortBy{ case (id,_) => id.value }.map{ case (_,json) => json }

    val content : String = Json.prettyPrint(Json.obj(
      "name" -> "foo",
      "patterns" -> patterns
    ))

    content
  }

  private val enabledChecks : List[Pattern.Id] = List(
    "Custom_Scala_GetCalls",
    "Custom_Scala_NonFatal",
    "Custom_Scala_FieldNamesChecker"
  ).map(s => Pattern.Id(s))

  // private val enabledChecks : List[Pattern.Id] = newPatterns.keys.toList

  private val checkParsedFile : Source => (Pattern.Id, Pattern) => List[Result] =
    tree => (id, pat) =>
      Try(pat.apply(tree)) match {
        case Success(resList) => resList.map { res =>
          Result.Issue(
            com.codacy.plugins.api.Source.File(f.getAbsolutePath()),
            Result.Message(res.message.value),
            id,
            com.codacy.plugins.api.Source.Line(res.position.startLine + 1)
          )
        }.toList.distinct
        case Failure(t) =>
          List(Result.FileError(
            com.codacy.plugins.api.Source.File(f.getAbsolutePath()),
            Option(ErrorMessage(t.getMessage)))
          )
      }

  def main(args : Array[String]) : Unit = {

    println(patternsJson(getClass().getClassLoader().asInstanceOf[URLClassLoader]))

    println(newPatterns.keys.mkString("\n"))

    val content : String = new String(Files.readAllBytes(f.toPath()))

    Try { content.parse[Source] } match {
      case Success(Parsed.Success(tree)) =>
        println("parsed ok")
        val r : List[Result] = newPatterns.filterKeys(enabledChecks.contains).flatMap { case (id, pat) =>
          checkParsedFile(tree)(id, pat)
        }.toList

        Try(r) match {
          case Success(results) =>
            println(results.mkString("\n"))
          case Failure(t) =>
            println(t.getMessage())
        }

      case Success(Parsed.Error(position, message, dtails)) =>
        println("parser error")
      case Failure(e) =>
        println(s"failed : $e")
    }
  }
}

