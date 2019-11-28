package com.malinskiy.marathon.android.executor.listeners

import com.malinskiy.marathon.android.model.TestIdentifier
import com.malinskiy.marathon.steps.StepResultConverter
import com.malinskiy.marathon.steps.StepsResultsListener
import com.malinskiy.marathon.steps.StepsResultsProvider


class StepsResultsJsonListener(
    private val stepResultConverter: StepResultConverter
) : NoOpTestRunListener(), StepsResultsProvider {

    companion object {
        private const val INSTRUMENTATION_STATUS_KEY_STEPS_RESULTS_JSON = "marathon.stepsResultsJson"
        private const val EMPTY_STEPS_RESULTS_JSON = "[]"
    }

    private val stepsResultsListeners = mutableListOf<StepsResultsListener>()

    override fun registerListener(listener: StepsResultsListener) {
        stepsResultsListeners.add(listener)
    }


    override fun testEnded(test: TestIdentifier, testMetrics: Map<String, String>) {
        val stepsResultsJson = testMetrics[INSTRUMENTATION_STATUS_KEY_STEPS_RESULTS_JSON] ?: EMPTY_STEPS_RESULTS_JSON
        val stepsResults = stepResultConverter.fromJson(stepsResultsJson)

        stepsResultsListeners.forEach { it.onStepsResults(test.toTest(), stepsResults) }
    }

}