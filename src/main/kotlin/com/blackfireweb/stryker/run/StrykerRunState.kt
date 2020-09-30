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
import com.intellij.javascript.nodejs.*
import com.intellij.javascript.nodejs.interpreter.NodeCommandLineConfigurator
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.npm.NpmUtil
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageRef
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager.*
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.io.File
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*

class StrykerRunState(private val myEnv: ExecutionEnvironment, private val myRunConfiguration: StrykerRunConfig) : RunProfileState {
    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult? {
        try {
            val interpreter: NodeJsInterpreter = NodeJsInterpreterRef.create(this.myRunConfiguration.getPersistentData().nodeJsRef).resolveNotNull(myEnv.project)
            val commandLine = NodeCommandLineUtil.createCommandLine(if (SystemInfo.isWindows) false else null)
            val reporter = myRunConfiguration.getStrykerIntelliJReporterFile()
            this.configureCommandLine(commandLine, interpreter, reporter)
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
        commandLine.charset = StandardCharsets.UTF_8
        val data = this.myRunConfiguration.getPersistentData()

        val workingDirectory = data.getWorkingDirectory()
        if (workingDirectory.isNotBlank()) {
            commandLine.withWorkDirectory(workingDirectory)
        }

        getInstance(this.myProject).modules.forEach {
            val thisModule = it
            ModuleRootManager.getInstance(it).contentRoots.forEach {
                ModuleRootModificationUtil.updateExcludedFolders(
                        thisModule,
                        it,
                        Collections.emptyList(),
                        Collections.singletonList(if (!workingDirectory.isBlank()) "file://${workingDirectory}/.stryker-tmp" else "${it.url}/.stryker-tmp"))
            }
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

        val packageToUse = workingDirectory.takeIf { it -> it.isNotEmpty() }
                ?.let { directory -> VfsUtil.findFileByURL(URL(if (workingDirectory.isNotEmpty()) { "file://${directory}/src" } else {""})) }
                ?.let { virtualFile -> NodeModuleSearchUtil.resolveModuleFromNodeModulesDir(virtualFile, "@stryker-mutator/core", NodeModuleDirectorySearchProcessor.PROCESSOR) }
                ?.let { resolvedInfo -> NodePackage(resolvedInfo.moduleSourceRoot.path) }
                ?: NodePackage.findDefaultPackage(myProject, "@stryker-mutator/core", interpreter)

        // As of Version 4 of Stryker, we can append a new plugin without destroying existing ones. So we can use a
        // bundled version of the Progress.js plugin instead of asking the user to download their own
        if (packageToUse?.version?.major!! >= 4) {
            val resource = this.javaClass.getResourceAsStream("/Progress.js")
            val tempReporterFile = File.createTempFile("stryker-intellij-reporter-", ".js")
            tempReporterFile.deleteOnExit()
            resource.use { fileOut -> fileOut.copyTo(tempReporterFile.outputStream()) }

            commandLine.addParameter("--appendPlugins")
            commandLine.addParameter(tempReporterFile.absolutePath)

            commandLine.addParameter("--reporters")
            commandLine.addParameter("intellij")
        } else {
            reporter?.let {
                commandLine.addParameter("--reporters")
                commandLine.addParameter("intellij")
            }
        }
        if ((data.kind == StrykerRunConfig.TestKind.TEST || data.kind == StrykerRunConfig.TestKind.SPEC) && !isConfigFile(data.specFile
                        ?: "")) {
            addMutateOrDie(commandLine, data)
        }

        if (data.kind == StrykerRunConfig.TestKind.DIRECTORY) {
            addMutateDirectoryOrDie(commandLine, data)
        }

        NodeCommandLineConfigurator.find(interpreter).configure(commandLine)
    }

    private fun addMutateOrDie(commandLine: GeneralCommandLine, data: StrykerRunConfig.StrykerRunSettings) {
        val file = data.specFile ?: return
        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(File(file)) ?: return
        val extension = virtualFile.extension

        commandLine.addParameter("--mutate")
        commandLine.addParameter(if (".spec.$extension\$".toRegex().containsMatchIn(file)) file.replace(".spec.$extension", ".$extension") else file)

    }

    private fun addMutateDirectoryOrDie(commandLine: GeneralCommandLine, data: StrykerRunConfig.StrykerRunSettings) {
        val directory = data.specsDir ?: return

        commandLine.addParameter("--mutate")
        commandLine.addParameter("${directory}/**/*.ts,js,tsx,jsx,!${directory}/**/*.spec.ts,spec.js,spec.tsx,spec.jsx,!${directory}/**/*.test.ts,test.js,test.tsx,test.jsx")
    }
}
