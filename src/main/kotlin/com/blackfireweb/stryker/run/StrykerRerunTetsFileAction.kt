package com.blackfireweb.stryker.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView

class StrykerRerunFailedTestAction(consoleView: SMTRunnerConsoleView, consoleProperties: StrykerConsoleProperties) :
    AbstractRerunFailedTestsAction(consoleView) {
    init {
        this.init(consoleProperties)
        this.model = consoleView.resultsViewer
    }

    override fun getRunProfile(environment: ExecutionEnvironment): MyRunProfile {
        val configuration = this.myConsoleProperties.configuration as StrykerRunConfig
        return object : MyRunProfile(configuration) {
            override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
                return StrykerRunState(environment, configuration)
//                val failedTests = getFailedTests(project).map { it.name }.filter { it !== "[root]" }.distinct()
//                return StrykerRunState(environment, configuration, failedTests)
            }
        }
    }
}
