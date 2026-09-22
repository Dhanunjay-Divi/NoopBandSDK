package com.noop.bandsdk

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size != 1) {
        System.err.println(
            "usage: noop-band-conformance <scenario|--list>",
        )
        exitProcess(2)
    }
    try {
        if (args[0] == "--list") {
            println(BandConformanceRunner.automatedScenarios.toJsonArray())
        } else {
            println(BandConformanceRunner.run(args[0]).toJson())
        }
    } catch (error: BandException) {
        System.err.println("conformance failed: ${error.category.wireValue}")
        exitProcess(1)
    } catch (_: Exception) {
        System.err.println("conformance failed: internalFailure")
        exitProcess(1)
    }
}

private fun List<String>.toJsonArray(): String =
    joinToString(prefix = "[", postfix = "]", separator = ",") {
        it.toJsonString()
    }

private fun String.toJsonString(): String = buildString {
    append('"')
    for (character in this@toJsonString) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}
