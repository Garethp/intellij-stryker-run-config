package com.blackfireweb.stryker.intentions

import com.blackfireweb.stryker.inspections.InspectionPsiLocationManager
import com.blackfireweb.stryker.run.MUTANT_PROTOCOL
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.execution.TestStateStorage
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

class DisableMutationQuickFix(private val locationManager: InspectionPsiLocationManager) : LocalQuickFix {
    override fun getName(): String {
        return "STRYKER:QUICKFIX"
    }

    override fun getFamilyName(): String = this.name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement;
        val newComment = JSPsiElementFactory.createPsiComment("/* Stryker disable */", element)
        val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile) ?: return

        val line = document.getLineNumber(element.textRange.startOffset)
        val lineEnd = document.getLineStartOffset(line)
        removeMutationProblem(project, line, element);

        document.insertString(lineEnd, "//Stryker disable-next-line\n")

    }

    private fun removeMutationProblem(project: Project, line: Int, element: PsiElement) {
        val storage = TestStateStorage.getInstance(project)
        val keys = storage.keys.filter { it.startsWith("$MUTANT_PROTOCOL://") }
            .filter { storage.getState(it)?.magnitude == 6 }
            .filter {
                val storedElement = locationManager
                    .getPsiLocation(it, element.containingFile, storage.getState(it)!!)?.first

                val document = PsiDocumentManager.getInstance(project).getDocument(storedElement!!.containingFile)!!

                val startLine = document.getLineNumber(storedElement.textRange.startOffset)

                startLine == line
            }
            .forEach( {storage.removeState(it) })
    }
}