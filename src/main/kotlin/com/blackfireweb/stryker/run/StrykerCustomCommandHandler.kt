package com.blackfireweb.stryker.run

import com.intellij.execution.Executor
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.terminal.TerminalShellCommandHandler
import com.intellij.util.execution.ParametersListUtil

class StrykerCustomCommandHandler : TerminalShellCommandHandler {
    private val canRun = ApplicationInfo.getInstance().build.baselineVersion >= 201

    @Override
    override fun execute(project: Project, workingDirectory: String?, localSession: Boolean, command: String, executor: Executor): Boolean = run(project, workingDirectory, command)

    @Override
    fun execute(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean = run(project, workingDirectory, command)


    private fun run(project: Project, workingDirectory: String?, command: String): Boolean {
        val config: StrykerRunConfig = StrykerConfigurationType().configurationFactory.createTemplateConfiguration(project) as StrykerRunConfig

        val commands = ParametersListUtil.parse(command)
        assert(commands.size >= 2)

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
        val runnerAndConfig = RunManager.getInstance(project).createConfiguration(config, StrykerConfigurationType().configurationFactory)

        ProgramRunnerUtil.executeConfiguration(
                runnerAndConfig,
                DefaultRunExecutor.getRunExecutorInstance()
        )

        return true
    }


    override fun matches(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean {
        if (!canRun) return false

        val triggers = arrayOf("stryker run", "npx stryker run")

        if (workingDirectory === null) return false
        if (!triggers.any { command.startsWith(it) }) return false

        return true
    }
}