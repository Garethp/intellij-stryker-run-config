package com.blackfireweb.stryker.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.SuppressionUtil
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.JavaScriptBundle
import com.intellij.lang.javascript.linter.JSLinterError
import com.intellij.lang.javascript.linter.JSLinterSuppressionUtil
import com.intellij.lang.javascript.psi.JSStatement
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.NotNull
import java.util.regex.Pattern
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod

class StrykerSuppressionUtil private constructor() : JSLinterSuppressionUtil() {
    override fun getToolName(): String {
        return JavaScriptBundle.message("settings.javascript.linters.eslint.configurable.name", *arrayOfNulls(0))
    }

    override fun getRulesFromFileLevelComment(@NotNull element: PsiComment): String? {
        return if (element.node.elementType !== JSTokenTypes.C_STYLE_COMMENT) {
            null
        } else {
            val text = element.text
            if (text.contains("Stryker disable-next-line")) {
                val matcher = FILE_SUPPRESSION_PATTERN.matcher(text)
                if (matcher.matches()) {
                    return matcher.group(1).trim { it <= ' ' }
                }
            }
            null
        }
    }

    override fun getRulesFromLineSuppressionComment(@NotNull comment: PsiComment): String? {
        val text = comment.text
        if (text.contains("Stryker disable-next-line")) {
            val matcher = LINE_SUPPRESSION_PATTERN.matcher(text)
            if (matcher.matches()) {
                return matcher.group(1).trim { it <= ' ' }
            }
        }
        return null
    }

    override fun buildFileCommentText(existing: String?, toAdd: String?): String {
        return getCommentText("Stryker disable-next-line", existing, toAdd)
    }

    override fun buildLineCommentText(ruleCode: String?, existingSuppressions: String?): String {
        return getCommentText("Stryker disable-next-line", existingSuppressions, ruleCode)
    }

    companion object {
        val INSTANCE = StrykerSuppressionUtil()
        private const val FILE_COMMENT_PREFIX = "Stryker disable-next-line"
        const val LINE_COMMENT_PREFIX = "Stryker disable-next-line"
        public val LINE_SUPPRESSION_PATTERN =
            Pattern.compile("(?://|/\\*)\\s*Stryker disable-next-line\\s*(?:\\[(.*?)])?(?:\\*/)?")
        private val FILE_SUPPRESSION_PATTERN = Pattern.compile("/\\*\\s*Stryker disable-next-line\\s*(?:\\[(.*?)])?\\s*\\*/")
        private fun getCommentText(@NotNull prefix: String, existing: String?, toAdd: String?): String {
            return if (toAdd == null) {
                prefix
            } else {
                val rules: String = if (StringUtil.isEmpty(existing)) toAdd else "$existing,$toAdd"
                val var10000 = prefix + if (StringUtil.isEmpty(rules)) "" else " [$rules]"
                var10000
            }
        }

        fun isMutationSuppressed(element: PsiElement, mutator: String) = SuppressionUtil.getStatementToolSuppressedIn(element, mutator,
            JSStatement::class.java, LINE_SUPPRESSION_PATTERN) != null

    }

    fun getSuppressionActionForLineCompatible(
        error: JSLinterError,
        documentModificationStamp: Long
    ): IntentionAction? {
        val method = this::class.memberFunctions.find {
            it.name == "getHighPrioritySuppressForLineAction" || it.name == "getSuppressForLineAction"
        } ?: return null

        return method.call(this, error, documentModificationStamp) as IntentionAction?
    }
}
