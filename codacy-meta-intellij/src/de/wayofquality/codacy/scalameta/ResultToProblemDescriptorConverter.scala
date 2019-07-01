package de.wayofquality.codacy.scalameta

import com.codacy.plugins.api.results.Result
import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.editor.Document
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}

case class ResultToProblemDescriptorConverter(
  file: PsiFile,
  manager: InspectionManager,
  descriptions : Map[String, CheckDescription]
) {

  private val document: Document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file)

  private def levelToProblemtype(s: String): ProblemHighlightType = s match {
    case "error" => ProblemHighlightType.ERROR
    case "warning" => ProblemHighlightType.WARNING
    case "info" => ProblemHighlightType.WEAK_WARNING
    case _ => ProblemHighlightType.GENERIC_ERROR_OR_WARNING
  }

  def toProblemDescriptor(res: Result): Option[ProblemDescriptor] = {

    res match {
      case issue: Result.Issue =>

        val correctLine = if (issue.line.value > 0) {
          issue.line.value - 1
        } else {
          0
        }

        println("*" * 80)
        val start = document.getLineStartOffset(correctLine)
        val end = document.getLineEndOffset(correctLine)

        println(s"$start - $end, [${document.getText().substring(start, end)}]")

        val element = file.findElementAt(start).getParent
        println(element.getText())

        val sameLineChildren = element.getChildren.filter { e =>
          start <= e.getTextRange().getStartOffset() && e.getTextRange().getEndOffset() <= end
        }

        println(sameLineChildren.mkString("--", "\n---\n", ""))

        val desc : ProblemDescriptor = manager.createProblemDescriptor(
          sameLineChildren.head,
          issue.message.value,
          Array.empty[LocalQuickFix],
          levelToProblemtype(descriptions.get(issue.patternId.value).map(_.level).getOrElse("unknown")),
          true,
          false
        )
        println("created description")

        println("*" * 80)
        Some(desc)

      case _ => None
    }
  }
}