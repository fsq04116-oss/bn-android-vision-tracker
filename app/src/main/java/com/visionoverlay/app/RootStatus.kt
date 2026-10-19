package com.visionoverlay.app

import java.io.File
import java.util.concurrent.TimeUnit

object RootStatus {
    @Volatile private var cached = false
    fun available(): Boolean = cached
    // Call off the UI thread. A missing/blocked su must not stall startup.
    fun refresh(): Boolean {
        cached = try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val done = process.waitFor(2, TimeUnit.SECONDS)
            if (!done) process.destroy()
            done && process.exitValue() == 0
        } catch (_: Exception) { false }
        return cached
    }
    fun applyThermalTuning(selinux: Boolean, cpu: Boolean, gpu: Boolean): Boolean {
        if (!available()) return false
        val commands = buildList {
            if (selinux) add("setenforce 0")
            if (cpu) add("for p in /sys/devices/system/cpu/cpu*/cpufreq/scaling_min_freq; do [ -w \$p ] && echo 0 > \$p; done")
            if (gpu) add("for p in /sys/class/kgsl/kgsl-3d0/devfreq/min_freq; do [ -w \$p ] && echo 0 > \$p; done")
        }
        return try { Runtime.getRuntime().exec(arrayOf("su", "-c", commands.joinToString(";"))).waitFor() == 0 } catch (_: Exception) { false }
    }
}
