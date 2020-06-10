package com.blackfireweb.stryker.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.ui.ConsoleView
import com.intellij.javascript.nodejs.NodeCommandLineUtil
import com.intellij.javascript.nodejs.NodeConsoleAdditionalFilter
import com.intellij.javascript.nodejs.NodeStackTraceFilter
import com.intellij.javascript.nodejs.interpreter.NodeCommandLineConfigurator
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.npm.NpmUtil
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageRef
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import java.io.File
import java.nio.charset.StandardCharsets

class StrykerRunState(private val myEnv: ExecutionEnvironment, private val myRunConfiguration: StrykerRunConfig) : RunProfileState {
    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult? {
        try {
            val interpreter: NodeJsInterpreter = NodeJsInterpreterRef.create(this.myRunConfiguration.getPersistentData().nodeJsRef).resolveNotNull(myEnv.project)
            val commandLine = NodeCommandLineUtil.createCommandLine(if (SystemInfo.isWindows) false else null)
            val reporter = myRunConfiguration.getStrykerIntelliJReporterFile()
            var onlyElement = this.configureCommandLine(commandLine, interpreter, reporter)
            val processHandler = NodeCommandLineUtil.createProcessHandler(commandLine, false)
            val consoleProperties = StrykerConsoleProperties(this.myRunConfiguration, this.myEnv.executor, StrykerTestLocationProvider(), NodeCommandLineUtil.shouldUseTerminalConsole(processHandler))
            val consoleView: ConsoleView = if (reporter != null) this.createSMTRunnerConsoleView(commandLine.workDirectory, consoleProperties) else ConsoleViewImpl(myProject, false)

            ProcessTerminatedListener.attach(processHandler)
            consoleView.attachToProcess(processHandler)
            val executionResult = DefaultExecutionResult(consoleView, processHandler)
            return executionResult
        } catch (e: Exception) {
            logger<StrykerRunState>().error("Failed to run Stryker configuration", e)
            throw e
        }

    }

    private val myProject = myEnv.project

    private fun createSMTRunnerConsoleView(workingDirectory: File?, consoleProperties: StrykerConsoleProperties): ConsoleView {
        val consoleView: ConsoleView = SMTestRunnerConnectionUtil.createConsole(consoleProperties.testFrameworkName, consoleProperties)
        consoleProperties.addStackTraceFilter(NodeStackTraceFilter(this.myProject, workingDirectory))
        consoleProperties.stackTrackFilters.forEach { consoleView.addMessageFilter(it) }
        consoleView.addMessageFilter(NodeConsoleAdditionalFilter(this.myProject, workingDirectory))
        Disposer.register(this.myProject, consoleView)
        return consoleView
    }

    private fun configureCommandLine(commandLine: GeneralCommandLine, interpreter: NodeJsInterpreter, reporter: NodePackage?) {
        var onlyFile: PsiElement? = null
        commandLine.charset = StandardCharsets.UTF_8
        val data = this.myRunConfiguration.getPersistentData()

        val workingDirectory = data.getWorkingDirectory()
        if (workingDirectory.isNotBlank()) {
            commandLine.withWorkDirectory(workingDirectory)
        }

        NodeCommandLineUtil.configureUsefulEnvironment(commandLine)
        val startCmd = "run"
        data.npmRef
                .takeIf { it?.isNotEmpty() ?: false }
                ?.let { NpmUtil.resolveRef(NodePackageRef.create(it), myProject, interpreter) }
                ?.let { pkg ->
                    val yarn = NpmUtil.isYarnAlikePackage(pkg)
                    val validNpmCliJsFilePath = NpmUtil.getValidNpmCliJsFilePath(pkg)
                    if (yarn) {
                        commandLine.withParameters(validNpmCliJsFilePath, "run")
                    } else {
                        commandLine.withParameters(validNpmCliJsFilePath.replace("npm-cli", "npx-cli"))
                    }
                    commandLine.addParameter("stryker")
                }
                ?: commandLine.withParameters(NodePackage.findDefaultPackage(myProject, "stryker", interpreter)!!.systemDependentPath + "/bin/stryker")

        commandLine.addParameter(startCmd)
        if (data.additionalParams.isNotBlank()) {
            val params = data.additionalParams.trim().split("\\s+".toRegex()).toMutableList()
            commandLine.withParameters(params)
        }
        EnvironmentVariablesData.create(data.envs, data.passParentEnvs).configureCommandLine(commandLine, true)
        reporter?.let {
            commandLine.addParameter("--reporters")
            commandLine.addParameter("intellij")
        }
        if (data.kind == StrykerRunConfig.TestKind.TEST || data.kind == StrykerRunConfig.TestKind.SPEC) {
            addMutateOrDie(commandLine, data)
        }

        if (data.kind == StrykerRunConfig.TestKind.DIRECTORY) {
            addMutateDirectoryOrDie(commandLine, data);
        }

        NodeCommandLineConfigurator.find(interpreter).configure(commandLine)
    }

    private fun addMutateOrDie(commandLine: GeneralCommandLine, data: StrykerRunConfig.StrykerRunSettings) {
        var file = data.specFile ?: return
        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(File(file)) ?: return
        val extension = virtualFile.extension;

        commandLine.addParameter("--mutate")
        commandLine.addParameter(if (".spec.$extension\$".toRegex().containsMatchIn(file)) file.replace(".spec.$extension", ".$extension") else file)

    }

    private fun addMutateDirectoryOrDie(commandLine: GeneralCommandLine, data: StrykerRunConfig.StrykerRunSettings) {
        var directory = data.specsDir ?: return

        commandLine.addParameter("--mutate")
        commandLine.addParameter("${directory}/**/*.ts,js,tsx,jsx,!${directory}/**/*.spec.ts,spec.js,spec.tsx,spec.jsx,!${directory}/**/*.test.ts,test.js,test.tsx,test.jsx")
    }
}
