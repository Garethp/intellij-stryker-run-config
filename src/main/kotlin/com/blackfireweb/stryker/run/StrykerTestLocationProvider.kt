package com.blackfireweb.stryker.run

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.javascript.testFramework.JsTestFileByTestNameIndex
import com.intellij.javascript.testFramework.jasmine.JasmineFileStructureBuilder
import com.intellij.javascript.testFramework.util.EscapeUtils
import com.intellij.javascript.testFramework.util.JsTestFqn
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSTestFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocCommentBase
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.ContainerUtil

class StrykerTestLocationProvider : SMTestLocator {
    override fun getLocation(protocol: String, path: String, metaInfo: String?, project: Project, scope: GlobalSearchScope): List<Location<*>> {
        return if (MUTANT_PROTOCOL != protocol) {
            emptyList()
        } else {
            val location = this.getTestLocation(project, path)
            return ContainerUtil.createMaybeSingletonList(location)
        }
    }

    override fun getLocation(protocol: String, path: String, project: Project, scope: GlobalSearchScope): List<Location<*>> {
        throw IllegalStateException("Should not be called")
    }

    private fun getTestLocation(project: Project, locationData: String): Location<*>? {
        val (fileName, start) = transformLocationData(locationData)

        val fullFileName = if (fileName.indexOf(project.basePath!!) == 0) { fileName } else { project.basePath + "/" + fileName }
        val (startLine, startColumn) = start
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(fullFileName)
        if (virtualFile === null) return null

        val file = PsiManager.getInstance(project).findFile(virtualFile)
        if (file === null) return null

        val startOffset = PsiDocumentManager.getInstance(project).getDocument(file)?.getLineStartOffset(startLine - 1)?.plus(startColumn - 1) ?: 0

        return PsiLocation.fromPsiElement(file.findElementAt(startOffset))
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
}