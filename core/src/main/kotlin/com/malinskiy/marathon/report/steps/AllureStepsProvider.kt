package com.malinskiy.marathon.report.steps

import com.malinskiy.marathon.test.Test
import io.qameta.allure.model.StepResult


interface AllureStepsProvider {
    fun registerListener(listener: AllureStepsListener)
}

interface AllureStepsListener {
    fun onAllureSteps(test: Test, steps: List<StepResult>)
}
