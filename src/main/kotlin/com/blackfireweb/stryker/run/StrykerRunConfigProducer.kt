package com.blackfireweb.stryker.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.javascript.testing.JsTestRunConfigurationProducer
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.text.nullize
import kotlin.reflect.KProperty1

const val strykerConfigFile = "stryker.conf.js"

class StrykerRunConfigProducer : JsTestRunConfigurationProducer<StrykerRunConfig>(listOf("stryker")) {
    override fun isConfigurationFromCompatibleContext(configuration: StrykerRunConfig, context: ConfigurationContext): Boolean {
        val psiElement = context.psiLocation ?: return false
        val projectRoot = findFileUpwards((psiElement as? PsiDirectory)?.virtualFile
                ?: psiElement.containingFile?.virtualFile
                ?: return false, strykerConfigFile) ?: return false
        val thatData = configuration.getPersistentData()
        val thisData = createTestElementRunInfo(psiElement, StrykerRunConfig.StrykerRunSettings(), projectRoot.path)?.mySettings
                ?: return false
        if (thatData.kind != thisData.kind) return false
        val compare: (KProperty1<StrykerRunConfig.StrykerRunSettings, String?>) -> Boolean = { it.get(thatData).nullize(true) == it.get(thisData).nullize(true) }
        return when (thatData.kind) {
            StrykerRunConfig.TestKind.DIRECTORY -> compare(StrykerRunConfig.StrykerRunSettings::specsDir)
            StrykerRunConfig.TestKind.SPEC -> compare(StrykerRunConfig.StrykerRunSettings::specFile)
            StrykerRunConfig.TestKind.TEST -> false
        }
    }

    private fun createTestElementRunInfo(element: PsiElement, templateRunSettings: StrykerRunConfig.StrykerRunSettings, projectRoot: String): StrykerTestElementInfo? {
        val virtualFile = PsiUtilCore.getVirtualFile(element) ?: return null
        templateRunSettings.setWorkingDirectory(projectRoot)
        val containingFile = element.containingFile as? JSFile ?: return if (virtualFile.isDirectory) {
            templateRunSettings.kind = StrykerRunConfig.TestKind.DIRECTORY
            templateRunSettings.specsDir = virtualFile.canonicalPath
            return StrykerTestElementInfo(templateRunSettings, null)
        } else null

        val textRange = element.textRange ?: return null

        val path = findTestByRange(containingFile, textRange)
        if (path == null) {
            templateRunSettings.kind = StrykerRunConfig.TestKind.SPEC
            templateRunSettings.specFile = containingFile.virtualFile.canonicalPath
            return StrykerTestElementInfo(templateRunSettings, containingFile)
        }
        templateRunSettings.specFile = virtualFile.path
        templateRunSettings.kind = if (path.testName != null || path.suiteNames.isNotEmpty() ) StrykerRunConfig.TestKind.TEST else StrykerRunConfig.TestKind.SPEC
        templateRunSettings.allNames = path.allNames
        if (templateRunSettings.kind == StrykerRunConfig.TestKind.TEST) {
            return null;
        }
        return StrykerTestElementInfo(templateRunSettings, path.testElement)
    }

    class StrykerTestElementInfo(val mySettings: StrykerRunConfig.StrykerRunSettings, val myEnclosingElement: PsiElement?)

    override fun setupConfigurationFromCompatibleContext(configuration: StrykerRunConfig, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
        val psiElement = context.psiLocation ?: return false
        val projectRoot = findFileUpwards((psiElement as? PsiDirectory)?.virtualFile
                ?: psiElement.containingFile?.virtualFile
                ?: return false, strykerConfigFile) ?: return false
        val runInfo = createTestElementRunInfo(psiElement, configuration.getPersistentData(), projectRoot.path) ?: return false
        configuration.setGeneratedName()
        runInfo.myEnclosingElement?.let { sourceElement.set(it) }
        return true
    }

    override fun getConfigurationFactory(): ConfigurationFactory {
        return type.configurationFactory
    }
}