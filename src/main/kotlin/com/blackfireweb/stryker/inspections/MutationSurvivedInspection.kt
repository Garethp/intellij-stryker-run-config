package com.blackfireweb.stryker.inspections

import com.blackfireweb.stryker.intentions.DisableMutationQuickFix
import com.blackfireweb.stryker.run.MUTANT_PROTOCOL
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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.messages.MessageBusConnection

class MutationSurvivedInspection() : LocalInspectionTool(), ProjectManagerListener, Disposable {
    private val messageBus: MessageBusConnection = ApplicationManager.getApplication().messageBus.connect(this)
    private val locationManager: InspectionPsiLocationManager = InspectionPsiLocationManager()

    init {
        messageBus.subscribe(ProjectManager.TOPIC, this)
    }

    override fun projectOpened(project: Project) {
        val storage = TestStateStorage.getInstance(project)
        val keys = storage.keys.filter { it.startsWith("$MUTANT_PROTOCOL://") }
        keys.forEach { storage.removeState(it) }
    }

    override fun projectClosing(project: Project) {
        val storage = TestStateStorage.getInstance(project)
        val keys = storage.keys.filter { it.startsWith("$MUTANT_PROTOCOL://") }
        keys.forEach { storage.removeState(it) }
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
        val project = file.project
        val storage = TestStateStorage.getInstance(project)
        val oldStringBeginning =
            "$MUTANT_PROTOCOL://" + file.virtualFile.path.replace(project.basePath + "/", "") + "::"
        val stringBeginning = "$MUTANT_PROTOCOL://" + file.virtualFile.path + "::"
        val results = storage.keys

        val relevantResults = results
            .filter { it.startsWith(stringBeginning) || it.startsWith(oldStringBeginning) }
            .filter { storage.getState(it)?.magnitude == 6 }
            .mapNotNull { locationManager.getPsiLocation(it, file, storage.getState(it)!!) }
        return relevantResults
            .filter { it.first.isValid && it.first.containingFile !== null && it.second.isValid && it.second.containingFile !== null }
            .map { createProblemDescriptor(it, manager) }.toTypedArray()
    }

    private fun createProblemDescriptor(
        psiLocations: Pair<PsiElement, PsiElement>,
        manager: InspectionManager
    ): ProblemDescriptor {
        return manager.createProblemDescriptor(
            psiLocations.first,
            psiLocations.second,
            "Mutant Survived",
            ProblemHighlightType.GENERIC_ERROR,
            true,
            DisableMutationQuickFix(locationManager)
        )
    }

    override fun dispose() {
        messageBus.disconnect()
    }
}