package com.blackfireweb.stryker.run

import com.blackfireweb.stryker.run.ui.*
import com.intellij.execution.*
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.newui.PluginsTab
import com.intellij.javascript.nodejs.NodeModuleDirectorySearchProcessor
import com.intellij.javascript.nodejs.NodeModuleSearchUtil
import com.intellij.javascript.nodejs.interpreter.NodeInterpreterUtil
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.npm.NpmUtil
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.testFramework.interfaces.mochaTdd.MochaTddFileStructureBuilder
import com.intellij.javascript.testFramework.jasmine.JasmineFileStructureBuilder
import com.intellij.lang.javascript.modules.InstallNodeModuleQuickFix
import com.intellij.lang.javascript.modules.NpmPackageInstallerLight
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.SettingsEditorGroup
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import org.jdom.Element
import org.jetbrains.debugger.DebuggableRunConfiguration
import org.jetbrains.io.LocalFileFinder
import java.awt.Point
import java.io.File
import java.net.InetSocketAddress
import java.net.URL
import java.util.*
import javax.swing.event.HyperlinkEvent

class StrykerRunConfig(project: Project, factory: ConfigurationFactory) : LocatableConfigurationBase<StrykerConfigurationType>(project, factory, ""), CommonProgramRunConfigurationParameters, DebuggableRunConfiguration {

    private val reporterPackage = "stryker-intellij-reporter"

    private var myRunSettings: StrykerRunSettings = StrykerRunSettings()

    override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState? {
        val state = StrykerRunState(env, this)
        return state
    }

    override fun createDebugProcess(socketAddress: InetSocketAddress, session: XDebugSession, executionResult: ExecutionResult?, environment: ExecutionEnvironment): XDebugProcess {
        throw ExecutionException("Debug has not been implemented yet")
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        val group = SettingsEditorGroup<StrykerRunConfig>()
        group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), ConfigurableEditorPanel(this.project))
        return group
    }

    override fun readExternal(element: Element) {
        super<LocatableConfigurationBase>.readExternal(element)
        XmlSerializer.deserializeInto(this, element)
        XmlSerializer.deserializeInto(myRunSettings, element)

        EnvironmentVariablesComponent.readExternal(element, envs)
    }

    override fun writeExternal(element: Element) {
        super<LocatableConfigurationBase>.writeExternal(element)
        XmlSerializer.serializeInto(this, element)
        XmlSerializer.serializeInto(myRunSettings, element)

        EnvironmentVariablesComponent.writeExternal(element, envs)
    }

    override fun suggestedName(): String? {
        return actionName
    }

    override fun getActionName(): String? {
        return when  {
            myRunSettings.kind === TestKind.DIRECTORY -> "Mutate All Files in ${getRelativePath(project, myRunSettings.specsDir ?: return null)}"
            myRunSettings.kind === TestKind.SPEC && isConfigFile(myRunSettings.specFile ?: "") -> "Run Stryker"
            myRunSettings.kind === TestKind.SPEC -> "Mutate ${getRelativePath(project, myRunSettings.specFile ?: return null)}"
            myRunSettings.kind === TestKind.TEST -> "Mutate ${myRunSettings.allNames?.joinToString(" -> ") ?: return null}"
            else -> null
        }
    }

    private fun getRelativePath(project: Project, path: String): String {
        val file = LocalFileFinder.findFile(path)
        if (file != null && file.isValid) {
            val root = ProjectFileIndex.getInstance(project).getContentRootForFile(file)
            if (root != null && root.isValid) {
                val relativePath = VfsUtilCore.getRelativePath(file, root, File.separatorChar)
                relativePath?.let { return relativePath }
            }
        }
        return getLastPathComponent(path)
    }

    private fun getLastPathComponent(path: String): String {
        val lastIndex = path.lastIndexOf('/')
        return if (lastIndex >= 0) path.substring(lastIndex + 1) else path
    }

    fun getPersistentData(): StrykerRunSettings {
        return myRunSettings
    }


    fun getStrykerIntelliJReporterFile(): NodePackage? {
        val contextFile = getContextFile() ?: return null
        val info = NodeModuleSearchUtil.resolveModuleFromNodeModulesDir(contextFile, reporterPackage, NodeModuleDirectorySearchProcessor.PROCESSOR)
        if (info != null && info.moduleSourceRoot.isDirectory) {
            return NodePackage(info.moduleSourceRoot.path)
        }
        return null
    }

    private fun getContextFile(): VirtualFile? {
        val data = getPersistentData()
        return findFile(data.specFile ?: "")
                ?: findFile(data.specsDir ?: "")
                ?: VfsUtil.findFileByURL(URL(data.workingDirectory ?: ""))
    }


    private fun findFile(path: String): VirtualFile? =
            if (FileUtil.isAbsolute(path)) LocalFileSystem.getInstance().findFileByPath(path) else null


    interface TestKindViewProducer {
        fun createView(project: Project): TestKindView
    }

    enum class TestKind(val myName: String) : TestKindViewProducer {
        DIRECTORY("All in &directory") {
            override fun createView(project: Project) = DirectoryKindView(project)
        },
        SPEC("Spec &file") {
            override fun createView(project: Project) = SpecKindView(project)
        },
        TEST("Test") {
            override fun createView(project: Project) = TestView(project)
        },
    }

    override fun clone(): RunConfiguration {
        val clone = super.clone() as StrykerRunConfig
        clone.myRunSettings = myRunSettings.clone()
        return clone
    }

    data class StrykerRunSettings(val u: Unit? = null) : Cloneable {
        @JvmField
        var allNames: List<String>? = null

        @JvmField
        var specsDir: String? = null

        @JvmField
        var specFile: String? = null

        @JvmField
        var testName: String? = null

        @JvmField
        var workingDirectory: String? = null

        @JvmField
        var envs: MutableMap<String, String> = LinkedHashMap()

        @JvmField
        var additionalParams: String = ""

        @JvmField
        var passParentEnvs: Boolean = true

        @JvmField
        var nodeJsRef: String = NodeJsInterpreterRef.createProjectRef().referenceName

        @JvmField
        var npmRef: String? = NpmUtil.createProjectPackageManagerPackageRef().referenceName

        @JvmField
        var kind: TestKind = TestKind.SPEC

        public override fun clone(): StrykerRunSettings {
            try {
                val data = super.clone() as StrykerRunSettings
                data.envs = LinkedHashMap(envs)
                data.allNames = allNames?.toList()
                return data
            } catch (e: CloneNotSupportedException) {
                throw RuntimeException(e)
            }
        }

        fun getWorkingDirectory(): String = ExternalizablePath.localPathValue(workingDirectory)

        fun setWorkingDirectory(value: String?) {
            workingDirectory = ExternalizablePath.urlValue(value)
        }

        fun getSpecName(): String = specFile?.let { File(it).name } ?: ""

        fun setEnvs(envs: Map<String, String>) {
            this.envs.clear()
            this.envs.putAll(envs)
        }
    }

    private val reporterFound = Key<Boolean>("stryker-intellij-reporter_found")

    override fun checkConfiguration() {
        val data = getPersistentData()
        val workingDir = data.getWorkingDirectory()
        if (!File(workingDir).exists()) {
            throw RuntimeConfigurationWarning("Working directory '$workingDir' doesn't exist")
        }

        val interpreter: NodeJsInterpreter? = NodeJsInterpreterRef.create(data.nodeJsRef).resolve(project)
        NodeInterpreterUtil.checkForRunConfiguration(interpreter)
        if (data.kind == TestKind.DIRECTORY && data.specsDir.isNullOrBlank()) {
            throw RuntimeConfigurationError("Spec directory must be defined")
        }
        if (project.getUserData(reporterFound) != true) {
            if (getStrykerIntelliJReporterFile() == null) {
                val context = getContextFile()
                val fix = interpreter?.let { interpreter ->
                    context?.let { c ->
                        findFileUpwards(c, "node_modules")?.let { packageJson ->
                            Runnable {
                                val listener = InstallNodeModuleQuickFix.createListener(project, packageJson, reporterPackage)
                                val installerLight = ServiceManager.getService(NpmPackageInstallerLight::class.java) as NpmPackageInstallerLight
                                installerLight.installPackage(project, interpreter, reporterPackage, null as String?, File(packageJson.path), listener, "-D")
                            }
                        }
                    }
                }
                throw RuntimeConfigurationWarning("Package '$reporterPackage' not found under the Stryker project, test tab view will not be shown. Install the package to watch test execution and results", fix)
            } else {
                project.putUserData(reporterFound, true)
            }
        }
    }

    override fun getWorkingDirectory(): String? {
        return myRunSettings.getWorkingDirectory()
    }

    override fun getEnvs(): MutableMap<String, String> {
        return myRunSettings.envs
    }

    override fun setWorkingDirectory(value: String?) {
        myRunSettings.setWorkingDirectory(value)
    }

    override fun setEnvs(envs: MutableMap<String, String>) {
        myRunSettings.setEnvs(envs)
    }

    override fun isPassParentEnvs(): Boolean {
        return myRunSettings.passParentEnvs
    }

    override fun setPassParentEnvs(passParentEnvs: Boolean) {
        myRunSettings.passParentEnvs = passParentEnvs
    }

    override fun setProgramParameters(value: String?) {
        myRunSettings.additionalParams = value ?: ""
    }

    override fun getProgramParameters(): String? {
        return myRunSettings.additionalParams
    }
}

fun findFileUpwards(specName: VirtualFile, fileName: String): VirtualFile? {
    var cur = specName.parent
    while (cur != null) {
        if (cur.children.find {name -> name.name == fileName } != null) {
            return cur
        }
        cur = cur.parent
    }
    return null
}

fun findTestByRange(containingFile: JSFile, textRange: TextRange) =
        (JasmineFileStructureBuilder.getInstance().fetchCachedTestFileStructure(containingFile).findTestElementPath(textRange)
                ?: MochaTddFileStructureBuilder.getInstance().fetchCachedTestFileStructure(containingFile).findTestElementPath(textRange))
