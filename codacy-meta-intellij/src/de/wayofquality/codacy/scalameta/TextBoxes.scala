package de.wayofquality.codacy.scalameta

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, PlatformDataKeys}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

class TextBoxes extends AnAction("Text_Boxes") {

  override def actionPerformed(e: AnActionEvent): Unit = {
    val project : Project = e.getData(PlatformDataKeys.PROJECT_CONTEXT)
    val txt : String = Messages.showInputDialog(project, "What is your name?", "Input your name", Messages.getQuestionIcon())
    Messages.showMessageDialog(project, "Hello, " + txt + "!\n I am glad to see you.", "Information", Messages.getInformationIcon())
  }
}
