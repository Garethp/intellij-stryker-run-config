package com.blackfireweb.stryker.run

const val MUTANT_PROTOCOL = "stryker-mutant"

fun isConfigFile(name: String): Boolean {
    return when {
        name.endsWith("stryker.conf.js") -> true
        name.endsWith("stryker.conf.ts") -> true
        name.endsWith("stryker.conf.json") -> true
        else -> false
    }
}