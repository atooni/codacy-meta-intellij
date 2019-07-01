package de.wayofquality.codacy.scalameta

import codacy.base
import codacy.base.{Pattern, PatternCompanion}
import codacy.macros.Patterns
import com.codacy.plugins.api.results.Pattern
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json._

import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

/**
  * The Metadata for a Codacy check. We need this to construct the
  * Idea Problemdescriptor with the correct severity.
  * @param id The Pattern Id
  * @param category The pattern category
  * @param level The pattern severity
  */
case class CheckDescription(
  id : String,
  category : String,
  level : String
)

/**
  * Initialize the patterns for a given file in the workspace
  * @param file The file under inspection
  */
class PatternResolver(file : PsiFile) {

  private val log : Logger = LoggerFactory.getLogger(classOf[PatternResolver])

  // We need readers and writers for Regex expressions
  private implicit val regexReader: Reads[Regex] = Reads(
    _.validate[String].flatMap { raw =>
      Try(raw.r) match {
        case Success(regex) => JsSuccess(regex)
        case Failure(ex) => JsError(ex.getMessage)
      }
    }
  )

  private implicit val regexWriter: Writes[Regex] = Writes((r:Regex) => Json.toJson(r.toString))

  private def findPatternFile(project: Project) : Option[VirtualFile] = {
    val configFileName : String = "codacymeta.json"
    val possibleLocations: Seq[String] = Seq(".idea", "project")

    val root : Option[VirtualFile] = Option(project.getBaseDir)

    root.flatMap { r =>
      // We will look in these directories : .idea, project, project base directory
      val dirs : Seq[VirtualFile] =
        possibleLocations.flatMap(name => Option(r.findChild(name))) :+ r

      dirs.flatMap(d => Option(d.findChild(configFileName))).headOption
    }
  }


  // Get the configured parameters from the config file
  private lazy val rawPatterns : List[JsObject] = {

    val params : Map[Pattern.Id,List[JsObject]] = Patterns.params

    findPatternFile(file.getProject) match {
      case None =>
        List.empty
      case Some(vf) =>
        val json = Json.parse(vf.contentsToByteArray())
        val rawPatterns : Set[JsObject] = json.\("patterns").as[Set[JsObject]]

        rawPatterns.map{ jPattern =>
          val id = (jPattern \ "patternId").as[Pattern.Id]

          val patternParams : Option[List[JsObject]] = params.get(id).filter(_.nonEmpty)

          id -> patternParams.map{ params =>
            jPattern ++ Json.obj("parameters" -> params)
          }.getOrElse(jPattern)

        }.toList.sortBy{ case (id,_) => id.value }.map{ case (_,obj) => obj }
    }
  }

  lazy val descriptions : Map[String, CheckDescription] = {
    rawPatterns.map{ p =>

      val fields : Map[String, JsValue] = p.fields.toMap

      val id : String = fields.get("patternId").map(_.asInstanceOf[JsString].value).getOrElse("unknownId")
      val cat : String = fields.get("category").map(_.asInstanceOf[JsString].value).getOrElse("CodeStyle")
      val lvl : String = fields.get("level").map(_.asInstanceOf[JsString].value).getOrElse("Info")

      val desc : CheckDescription = CheckDescription(id, cat, lvl.toLowerCase())

      (desc.id, desc)
    }.toMap
  }

  // ToDo: Read parameters from json and configure patterns
  /**
    * Get the concrete pattern implementation for all configured patterns
    */
  lazy val patterns : Map[Pattern.Id, Pattern] =
    Patterns.fromResources.filterKeys(k => descriptions.get(k.value).isDefined).mapValues { f =>
      val pattern = f.fromConfiguration(f.defaultConfig)
      pattern
    }

}
