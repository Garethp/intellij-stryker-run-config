package com.blackfireweb.stryker.actions

import com.blackfireweb.stryker.run.StrykerConsoleProperties
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import kotlin.reflect.full.memberFunctions

class RerunSomeMutantsAction : AnAction() {
    private fun isTheCorrectContext(event: AnActionEvent): Boolean {
        val tree = event.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? SMTRunnerTestTreeView ?: return false
        val selectedTest = (tree.selectedTest as? SMTestProxy) ?: return false
        val testRoot = selectedTest.root ?: return false

        if (testRoot::class.memberFunctions.find { it.name == "getTestConsoleProperties" } == null) {
            return false
        }

        return testRoot.testConsoleProperties is StrykerConsoleProperties && !selectedTest.isPassed
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isVisible = isTheCorrectContext(event)
        super.update(event)
    }

    override fun actionPerformed(event: AnActionEvent) {
        if (!isTheCorrectContext(event)) return

        val tree = event.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? SMTRunnerTestTreeView ?: return

        val selectedTest = tree.selectedTest as SMTestProxy
        val root = selectedTest.root

        val childrenToReRun = selectedTest.collectChildren().plus(selectedTest)
            .filter { it.locationUrl !== null }
            .filter { !it.isPassed }

        val console = SMTestRunnerConnectionUtil.createConsole("StrykerTestRunner", root.testConsoleProperties)
        val rerunAction = (root.testConsoleProperties as StrykerConsoleProperties).createRerunFailedTestsAction(console, childrenToReRun)

        rerunAction.actionPerformed(event)
    }
}