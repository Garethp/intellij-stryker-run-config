package com.blackfireweb.stryker.run

import com.intellij.execution.Executor
import com.intellij.execution.Location
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.javascript.nodejs.npm.NpmUtil
import com.intellij.javascript.nodejs.packageJson.PackageJsonScriptReferenceSupport
import com.intellij.lang.javascript.buildTools.npm.NpmScriptsService
import com.intellij.lang.javascript.buildTools.npm.NpmScriptsUtil
import com.intellij.lang.javascript.buildTools.npm.NpmTaskTreeView
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.terminal.TerminalShellCommandHandler
import com.intellij.util.execution.ParametersListUtil

class StrykerCustomCommandHandler : TerminalShellCommandHandler {
    private val canRun = ApplicationInfo.getInstance().build.baselineVersion >= 201
    private val validCommands = HashMap<String, List<String>>()

    @Override
    override fun execute(
        project: Project,
        workingDirectory: String?,
        localSession: Boolean,
        command: String,
        executor: Executor
    ): Boolean = run(project, workingDirectory, command)

    @Override
    fun execute(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean =
        run(project, workingDirectory, command)

    private fun run(project: Project, workingDirectory: String?, command: String): Boolean {
        val config: StrykerRunConfig =
            StrykerConfigurationType().configurationFactory.createTemplateConfiguration(project) as StrykerRunConfig

        var commands = ParametersListUtil.parse(command)
        assert(commands.size >= 2)

        if (command.startsWith("yarn stryker run")) {
            commands.removeAt(0)
        }

        if (commands[0] == "yarn") {
            commands = getCommandForScript(project, workingDirectory!!, commands[1])
        }

        if (commands[0] == "stryker") {
            commands.removeAt(0)
            commands.removeAt(0)
        } else if (commands[0] == "npx") {
            commands.removeAt(0)
            commands.removeAt(0)
            commands.removeAt(0)
        }

        val iterator = commands.listIterator()
        iterator.forEach { currentCommand ->
            when {
                currentCommand == "--reporters" -> {
                    if (iterator.hasNext()) {
                        iterator.remove()
                        iterator.next()
                        iterator.remove()
                    }
                }
            }
        }

        val arguments = commands.joinToString(" ")

        val virtualFile = LocalFileSystem.getInstance().findFileByPath(workingDirectory ?: "") ?: return false
        config.workingDirectory = virtualFile.path
        config.getPersistentData().additionalParams = arguments
        config.checkConfiguration()
        val runnerAndConfig =
            RunManager.getInstance(project).createConfiguration(config, StrykerConfigurationType().configurationFactory)

        ProgramRunnerUtil.executeConfiguration(
            runnerAndConfig,
            DefaultRunExecutor.getRunExecutorInstance()
        )

        return true
    }


    override fun matches(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean {
        if (!canRun) return false

        val validCommands = getValidCommandsForPackageJSON(project, workingDirectory)

        val triggers = arrayOf("yarn stryker run", "stryker run", "npx stryker run")
            .plus(validCommands.map { "yarn $it" })

        if (workingDirectory === null) return false
        if (!triggers.any { command.startsWith(it) }) return false

        return true
    }

    private fun getValidCommandsForPackageJSON(project: Project, workingDirectory: String?): List<String> {
        if (workingDirectory == null) return emptyList()
        if (validCommands.containsKey(workingDirectory) && validCommands[workingDirectory]!!.isNotEmpty()) {
            return validCommands[workingDirectory]!!
        }

        val packageJSONFile = LocalFileSystem.getInstance().findFileByPath("$workingDirectory/package.json")
            ?: return emptyList()

        ApplicationManager.getApplication().invokeLater {
            val scripts = NpmScriptsUtil.listTasks(project, packageJSONFile).scripts.filter {
                val command = NpmScriptsUtil.findScriptProperty(project, packageJSONFile, it.name)!!.value!!.text
                command.removePrefix("\"").startsWith("stryker run")
            }

            validCommands[workingDirectory] = scripts.map { it.name }
        }

        return emptyList()
    }

    private fun getCommandForScript(project: Project, workingDirectory: String, script: String): List<String> {
        val packageJSONFile = LocalFileSystem.getInstance().findFileByPath("$workingDirectory/package.json")
            ?: return emptyList()
        val command = NpmScriptsUtil.findScriptProperty(project, packageJSONFile, script)!!.value!!.text
            .removePrefix("\"")
            .removeSuffix("\"")

        return ParametersListUtil.parse(command)
    }
}