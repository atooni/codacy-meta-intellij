package de.wayofquality.codacy.scalameta

import com.codacy.plugins.api.results.Result
import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.editor.Document
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile, PsiRecursiveElementVisitor}

import scala.collection.mutable

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

        val start = document.getLineStartOffset(correctLine)
        val end = document.getLineEndOffset(correctLine)

        val lineElements : mutable.ListBuffer[(PsiElement, Int)] = mutable.ListBuffer.empty

        val element = file.findElementAt(start).getParent

        element.accept(new PsiRecursiveElementVisitor() {
          override def visitElement(e: PsiElement): Unit = {
            if (start <= e.getTextRange().getStartOffset() && e.getTextRange().getEndOffset() <= end) {
              lineElements.append((e, e.getTextLength()))
            }
            e.acceptChildren(this)
          }
        })

        val lineElement : PsiElement = lineElements.sortBy(_._2).lastOption.map(_._1).getOrElse(file.findElementAt(start))

        val desc : ProblemDescriptor = manager.createProblemDescriptor(
          lineElement,
          issue.message.value,
          Array.empty[LocalQuickFix],
          levelToProblemtype(descriptions.get(issue.patternId.value).map(_.level).getOrElse("unknown")),
          true,
          false
        )
        Some(desc)

      case _ => None
    }
  }
}