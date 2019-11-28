package com.malinskiy.marathon.steps

import com.malinskiy.marathon.test.Test
import io.qameta.allure.model.StepResult


interface StepsResultsProvider {
    fun registerListener(listener: StepsResultsListener)
}

interface StepsResultsListener {
    fun onStepsResults(test: Test, stepsResults: List<StepResult>)
}