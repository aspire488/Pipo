package com.pipo.robot.ui.home

import com.pipo.robot.DebugFlags

/** Debug builds only: render layers to skip, to measure what each costs on a real phone (see [DebugFlags]). */
object PerfProbe {
    val skip: Set<String> get() = DebugFlags["skip"]
}
