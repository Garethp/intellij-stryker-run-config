package com.blackfireweb.stryker.run

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class StrykerErrorAnnotator : ExternalAnnotator<String, String>() {


//    fun annotate(element: PsiElement, holder: AnnotationHolder) {
////        holder.createErrorAnnotation(ASTNode(), "test error");
//        annotateRange(element, holder, 3, 3, 4, 32)
//
////        if (element.toString() == "ES6FromClause") {
//////            holder.newAnnotation(HighlightSeverity.ERROR, "Test").range(element.textRange)
//////            holder.newAnnotation(HighlightSeverity.ERROR, "Other Test").range(element.textRange)
////
////        }
////
////        if (element.toString() == "JSReferenceExpression" && element.text == "expect") {
////            val file = element.containingFile;
////            val document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file)
////                    holder.createErrorAnnotation(element, "test error");
////        }
//    }


    override fun collectInformation(file: PsiFile): String? {
        return "Broken"
    }

    override fun doAnnotate(collectedInfo: String?): String? {
        return "Broken"
    }

    override fun apply(file: PsiFile, annotationResult: String?, holder: AnnotationHolder) {
        annotateRange(file, holder, 0, 3, 0, 6)
    }

    private fun annotateRange(file: PsiFile, holder: AnnotationHolder, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int) {
        val document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file) ?: return

        val startRange = document.getLineStartOffset(startLine) + startColumn
        val endRange = document.getLineStartOffset(endLine) + endColumn

        holder.createErrorAnnotation(TextRange(startRange, endRange), "test error");
    }
}