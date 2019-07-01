package de.wayofquality.codacy.scalameta

import codacy.base.Pattern
import com.codacy.plugins.api.ErrorMessage
import com.codacy.plugins.api.results.{Pattern, Result}
import com.intellij.codeInspection._
import com.intellij.psi.PsiFile
import org.slf4j.{Logger, LoggerFactory}

import scala.meta._
import scala.meta.parsers.Parsed
import scala.util.{Failure, Success, Try}

class CodacyMetaChecker extends LocalInspectionTool {

  private val log: Logger = LoggerFactory.getLogger(classOf[CodacyMetaChecker])

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    val resolver : PatternResolver = new PatternResolver(file)
    val converter : ResultToProblemDescriptorConverter =
      ResultToProblemDescriptorConverter(file, manager, resolver.descriptions)
    val source: String = new String(file.getVirtualFile().contentsToByteArray())
    val filename: String = file.getVirtualFile().getPath()

    log.info(s"Running Codacy Meta Inspection on File [$filename]")

    val result : Array[ProblemDescriptor] = runCodacyChecks(source, filename, resolver.patterns) match {
      case Success(r) =>
        r.flatMap(converter.toProblemDescriptor).toArray

      case Failure(e) =>
        log.error(s"Failed to run Codacy Meta Inspection : [${e.getMessage()}]")
        Array.empty
    }
    result
  }

  /**
    * Check the scala code with a set of Codacy meta checks.
    * @param source The scala source to check read into String.
    * @param fileName A filename. This is only to construct the result message.
    * @param checks The checks that shall be run.
    * @return The List of issues, wrapped within a [[Try]]
    */
  def runCodacyChecks(
    source : String,
    fileName : String,
    checks : Map[Pattern.Id, Pattern]
  ) : Try[List[Result]] = {

    // This applies a single pattern to a parsed file and yields the Results for this pattern
    val checkParsedFile : Source => (Pattern.Id, Pattern) => List[Result] =
    tree => (id, pat) =>
      Try(pat.apply(tree)) match {
        case Success(resList) => resList.map { res =>
          Result.Issue(
            com.codacy.plugins.api.Source.File(fileName),
            Result.Message(res.message.value),
            id,
            com.codacy.plugins.api.Source.Line(res.position.startLine + 1)
          )
        }.toList.distinct
        case Failure(t) =>
          List(Result.FileError(
            com.codacy.plugins.api.Source.File(fileName),
            Option(ErrorMessage(t.getMessage)))
          )
      }

    // Try to parse the source with Scala Meta
    Try { source.parse[Source] } match {

      // If parsing was successful, apply all the patterns
      case Success(Parsed.Success(tree)) =>
        val r : List[Result] = checks.flatMap { case (id, pat) =>
          checkParsedFile(tree)(id, pat)
        }.toList

        Try(r)

      case Success(Parsed.Error(position, message, _)) =>
        val loc : String = s"${position.startLine}:${position.startColumn}"
        throw new Exception(s"Parser error in file [$fileName] at [$loc] : [$message]")
      case Failure(e) =>
        throw e
    }
  }
}

