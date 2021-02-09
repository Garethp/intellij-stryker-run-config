package com.blackfireweb.stryker.licencing

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import org.ini4j.Config.KEY_PREFIX

import com.intellij.ui.LicensingFacade


class LicencingManager {
    private val requiresPurchase = true

    private fun isLicensed(): Boolean {
        if (!requiresPurchase) return true

        val facade = LicensingFacade.getInstance() ?: return false
        val cstamp = facade.getConfirmationStamp("PSTRKER") ?: return false
        if (cstamp.startsWith(KEY_PREFIX) || cstamp.startsWith("eval:")) {
            // the license is obtained via JetBrainsAccount or entered as an activation code
            return true
        }

        return false
    }

    fun requestLicence(): Boolean {
        if (isLicensed())
            return true

        val actionManager = ActionManager.getInstance()

        // first, assume we are running inside the opensource version
        // first, assume we are running inside the opensource version
        var registerAction = actionManager.getAction("RegisterPlugins")
        if (registerAction == null) {
            // assume running inside commercial IDE distribution
            registerAction = actionManager.getAction("Register")
        }

        registerAction?.actionPerformed(
            AnActionEvent.createFromDataContext(
                "",
                Presentation(),
                DataContext { "Please register" }
            )
        )

        return isLicensed()
    }
}