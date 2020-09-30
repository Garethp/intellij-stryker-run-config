package com.blackfireweb.stryker.run

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.execution.TestStateStorage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.messages.MessageBusConnection

class MutationSurvivedInspection() : LocalInspectionTool(), ProjectManagerListener, Disposable {
    private val messageBus: MessageBusConnection = ApplicationManager.getApplication().messageBus.connect(this)

    init {
        messageBus.subscribe(ProjectManager.TOPIC, this)
    }

    override fun projectClosing(project: Project) {
        val storage = TestStateStorage.getInstance(project)
        val keys = storage.keys.filter { it.startsWith("$MUTANT_PROTOCOL://") }
        keys.forEach { storage.removeState(it) }
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        val project = file.project
        val storage = TestStateStorage.getInstance(project)

        val oldStringBeginning = "$MUTANT_PROTOCOL://" + file.virtualFile.path.replace(project.basePath + "/", "") + "::"
        val stringBeginning = "$MUTANT_PROTOCOL://" + file.virtualFile.path + "::"
        val results = storage.keys

        val relevantResults = results
                .filter { it.startsWith(stringBeginning) || it.startsWith(oldStringBeginning) }
                .filter { storage.getState(it)?.magnitude == 6 }

        return relevantResults.map { createProblemDescriptor(it, file, manager) }.toTypedArray()
    }

    private fun transformLocationData(locationData: String): Triple<String, Pair<Int, Int>, Pair<Int, Int>> {
        var splitted = locationData.split("::")
        val endRange = splitted.last()
        splitted = splitted.dropLast(1)
        val startRange = splitted.last()
        splitted = splitted.dropLast(1)
        val fileName = splitted.joinToString("::")

        val (startLine, startColumn) = startRange.split(":").map { it.toInt() }
        val (endLine, endColumn) = endRange.split(":").map { it.toInt() }

        return Triple(fileName, Pair(startLine, startColumn), Pair(endLine, endColumn))
    }

    private fun createProblemDescriptor(url: String, file: PsiFile, manager: InspectionManager): ProblemDescriptor {
        val (_, start, end) = transformLocationData(url)
        val (startLine, startColumn) = start
        val (endLine, endColumn) = end

        val startOffset = PsiDocumentManager.getInstance(file.project).getDocument(file)?.getLineStartOffset(startLine - 1)?.plus(startColumn - 1)
                ?: 0
        val endOffset = PsiDocumentManager.getInstance(file.project).getDocument(file)?.getLineStartOffset(endLine - 1)?.plus(endColumn - 2)
                ?: 0

        return manager.createProblemDescriptor(file.findElementAt(startOffset)!!, file.findElementAt(endOffset)!!, "Mutant Survived", ProblemHighlightType.GENERIC_ERROR, true)
    }

    override fun dispose() {
        messageBus.disconnect()
    }
}