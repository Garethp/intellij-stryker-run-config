package com.blackfireweb.stryker.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView

class StrykerRerunFailedTestAction(consoleView: SMTRunnerConsoleView, consoleProperties: StrykerConsoleProperties) :
    AbstractRerunFailedTestsAction(consoleView) {

    constructor(
        consoleView: SMTRunnerConsoleView,
        consoleProperties: StrykerConsoleProperties,
        testsToReRun: List<SMTestProxy>?
    ) : this(consoleView, consoleProperties) {
        this.testsToRerun = testsToReRun
        this.init(consoleProperties)
        this.model = consoleView.resultsViewer
    }

    private val CAN_RERUN = true
    private var testsToRerun: List<SMTestProxy>? = null

    init {
        this.init(consoleProperties)
        this.model = consoleView.resultsViewer
    }

    override fun getRunProfile(environment: ExecutionEnvironment): MyRunProfile {
        val configuration = this.myConsoleProperties.configuration as StrykerRunConfig
        return object : MyRunProfile(configuration) {
            override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
                if (!CAN_RERUN)
                    return StrykerRunState(environment, configuration)

                val failedTests = testsToRerun ?: getFailedTests(project)
                    .filter {it.name !== "[root]" }
                    .map { it.children }
                    .flatten()
                    .filter { !it.isPassed }

                return StrykerRunState(environment, configuration, failedTests)
            }
        }
    }
}
