package com.blackfireweb.stryker.intentions

import com.blackfireweb.stryker.inspections.InspectionPsiLocationManager
import com.blackfireweb.stryker.run.MUTANT_PROTOCOL
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.SuppressionUtil
import com.intellij.execution.TestStateStorage
import com.intellij.lang.javascript.linter.JSLinterError
import com.intellij.lang.javascript.psi.JSStatement
import com.intellij.lang.javascript.psi.impl.JSPsiElementFactory
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement


class DisableMutationQuickFix(private val locationManager: InspectionPsiLocationManager) : LocalQuickFix {
    override fun getName(): String {
        return "Disable this mutation"
    }

    override fun getFamilyName(): String = this.name

    @Suppress("RedundantSemicolon")
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement;
        val document: Document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile) ?: return

        val line = document.getLineNumber(element.textRange.startOffset)
        val column = element.textOffset - document.getLineStartOffset(line)

        val alreadySuppressed = SuppressionUtil.getStatementToolSuppressedIn(element, "StringMutator",
            JSStatement::class.java, StrykerSuppressionUtil.LINE_SUPPRESSION_PATTERN)

        val intention = StrykerSuppressionUtil.INSTANCE.getSuppressionActionForLineCompatible(
            JSLinterError(
                line + 1,
                column + 1,
                "test",
                "StringMutator"
            ), 123
        ) ?: return

        intention.invoke(project, FileEditorManager.getInstance(project).selectedTextEditor, element.containingFile)
    }
}