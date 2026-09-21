package com.noop.bandsdk

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size != 1) {
        System.err.println("usage: noop-band-conformance <scenario>")
        exitProcess(2)
    }
    try {
        println(BandConformanceRunner.run(args[0]).toJson())
    } catch (error: BandException) {
        System.err.println("conformance failed: ${error.category.wireValue}")
        exitProcess(1)
    } catch (_: Exception) {
        System.err.println("conformance failed: internalFailure")
        exitProcess(1)
    }
}
