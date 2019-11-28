package com.malinskiy.marathon.report.junit

import com.malinskiy.marathon.analytics.internal.sub.ExecutionReport
import com.malinskiy.marathon.analytics.internal.sub.TestEvent
import com.malinskiy.marathon.report.Reporter
import com.malinskiy.marathon.test.Test

internal class JUnitReporter(private val jUnitWriter: JUnitWriter) : Reporter {
    override fun generate(executionReport: ExecutionReport) {

        val testEvents = collectTestEvents(executionReport)
        testEvents.forEach { event ->
            jUnitWriter.testFinished(event.poolId, event.device, event.testResult)
        }
    }

    private fun collectTestEvents(executionReport: ExecutionReport): List<TestEvent> {
        val testEventsMap = mutableMapOf<Test, MutableList<TestEvent>>()

        executionReport.testEvents.forEach { event ->
            val storedEvents = testEventsMap[event.testResult.test] ?: mutableListOf()
            storedEvents += event

            testEventsMap[event.testResult.test] = storedEvents
        }

        return testEventsMap.flatMap { entry ->
            val successItem = entry.value.find { it.testResult.isSuccess }
            if (successItem == null) {
                entry.value
            } else {
                listOf(successItem)
            }
        }
    }
}
