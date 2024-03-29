package com.blackfireweb.stryker.run

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.ContainerUtil

class StrykerTestLocationProvider : SMTestLocator, PossiblyDumbAware {
    private val locations = HashMap<String, Pair<Location<*>, Location<*>>>()

    override fun isDumbAware(): Boolean = locations.isNotEmpty()

    override fun getLocation(protocol: String, path: String, metaInfo: String?, project: Project, scope: GlobalSearchScope): List<Location<*>> {
        return if (MUTANT_PROTOCOL != protocol) {
            emptyList()
        } else {
            val location = this.getTestLocation(project, path.removePrefix("$MUTANT_PROTOCOL://"))
            if (location === null)
                return emptyList()

            return listOf(location.first, location.second)
        }
    }

    override fun getLocation(protocol: String, path: String, project: Project, scope: GlobalSearchScope): List<Location<*>> {
        throw IllegalStateException("Should not be called")
    }

    private fun getTestLocation(project: Project, locationData: String): Pair<Location<*>, Location<*>>? {
        if (locations.containsKey(locationData)) {
            return locations[locationData]!!
        }

        val (fileName, start, end) = transformLocationData(locationData)

        val fullFileName = if (fileName.indexOf(project.basePath!!) == 0) { fileName } else { project.basePath + "/" + fileName }
        val (startLine, startColumn) = start
        val (endLine, endColumn) = end
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(fullFileName)
        if (virtualFile === null) return null

        val file = PsiManager.getInstance(project).findFile(virtualFile)
        if (file === null) return null

        val startOffset = PsiDocumentManager.getInstance(project).getDocument(file)?.getLineStartOffset(startLine - 1)?.plus(startColumn - 1) ?: 0
        val endOffset = PsiDocumentManager.getInstance(project).getDocument(file)?.getLineStartOffset(endLine - 1)?.plus(endColumn - 1) ?: 0

        locations[locationData] = Pair(PsiLocation.fromPsiElement(file.findElementAt(startOffset)), PsiLocation.fromPsiElement(file.findElementAt(endOffset)))
        return locations[locationData]!!
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