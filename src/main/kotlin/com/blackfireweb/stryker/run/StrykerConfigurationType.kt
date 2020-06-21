package com.blackfireweb.stryker.run

import com.blackfireweb.stryker.PluginIcons
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import javax.swing.Icon

val type = ConfigurationTypeUtil.findConfigurationType(StrykerConfigurationType::class.java)

class StrykerConfigurationType : ConfigurationTypeBase("StrykerConfigurationType", "Stryker", "Run Stryker Test", PluginIcons.STRYKER) {
    val configurationFactory: ConfigurationFactory

    override fun getDisplayName(): String = "Stryker"


    init {
        configurationFactory = object : ConfigurationFactory(this) {
            override fun getId(): String = "Stryker Run Config"

            override fun createTemplateConfiguration(p0: Project): RunConfiguration {
                return StrykerRunConfig(p0, this)
            }

            override fun getIcon(): Icon {
                return PluginIcons.STRYKER
            }

            override fun isApplicable(project: Project): Boolean {
                return true
            }
        }
        addFactory(configurationFactory)
    }
}

                                                                            