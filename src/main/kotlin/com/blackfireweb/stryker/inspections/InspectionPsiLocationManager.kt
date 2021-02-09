package com.blackfireweb.stryker.inspections

import com.intellij.execution.TestStateStorage
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.util.*
import kotlin.collections.HashMap

class InspectionPsiLocationManager {
    private var map: HashMap<String, Triple<Date, PsiElement, PsiElement>> = HashMap()

    fun getPsiLocation(location: String, file: PsiFile, state: TestStateStorage.Record): Pair<PsiElement, PsiElement>? {
        val lastLocation = map[location]

        if (lastLocation !== null && state.date.before(lastLocation.first)) {
            return null
        }

        if (!map.containsKey(location) || state.date.after(map[location]!!.first)) {
            val psiLocations = transformLocationData(location, file)
            map[location] = Triple(state.date, psiLocations.first, psiLocations.second)
        }

        return Pair(map[location]!!.second, map[location]!!.third)
    }

    private fun transformLocationData(locationData: String, file: PsiFile): Pair<PsiElement, PsiElement> {
        var splitted = locationData.split("::")
        val endRange = splitted.last()
        splitted = splitted.dropLast(1)
        val startRange = splitted.last()
        splitted = splitted.dropLast(1)

        val (startLine, startColumn) = startRange.split(":").map { it.toInt() }
        val (endLine, endColumn) = endRange.split(":").map { it.toInt() }

        val startOffset = PsiDocumentManager.getInstance(file.project).getDocument(file)?.getLineStartOffset(startLine - 1)?.plus(startColumn - 1)
            ?: 0
        val endOffset = PsiDocumentManager.getInstance(file.project).getDocument(file)?.getLineStartOffset(endLine - 1)?.plus(endColumn - 2)
            ?: 0

        return Pair(file.findElementAt(startOffset)!!, file.findElementAt(endOffset)!!)
    }
}