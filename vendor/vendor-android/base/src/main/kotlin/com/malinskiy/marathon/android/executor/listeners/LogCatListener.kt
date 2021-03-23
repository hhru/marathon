package com.malinskiy.marathon.android.executor.listeners

import com.malinskiy.marathon.android.AndroidDevice
import com.malinskiy.marathon.android.executor.listeners.line.LineListener
import com.malinskiy.marathon.android.model.TestIdentifier
import com.malinskiy.marathon.device.DevicePoolId
import com.malinskiy.marathon.device.toDeviceInfo
import com.malinskiy.marathon.execution.Attachment
import com.malinskiy.marathon.execution.AttachmentType
import com.malinskiy.marathon.report.attachment.AttachmentListener
import com.malinskiy.marathon.report.attachment.AttachmentProvider
import com.malinskiy.marathon.report.logs.LogWriter
import com.malinskiy.marathon.report.steps.AllureStepsListener
import com.malinskiy.marathon.report.steps.AllureStepsProvider
import com.malinskiy.marathon.report.steps.StepResultConverter

class LogCatListener(
    private val device: AndroidDevice,
    private val devicePoolId: DevicePoolId,
    private val logWriter: LogWriter
) : NoOpTestRunListener(), AttachmentProvider, LineListener, AllureStepsProvider {

    companion object {
        private const val ALLURE_STEPS_START_PREFIX = "#AllureStepsInfoJson#:"
        private const val ALLURE_STEPS_START_PREFIX_LENGTH = "#AllureStepsInfoJson#:".length
    }

    private val attachmentListeners = mutableListOf<AttachmentListener>()
    private val allureStepsListeners = mutableListOf<AllureStepsListener>()
    private val stepsResultConverter = StepResultConverter()


    override fun registerListener(listener: AttachmentListener) {
        attachmentListeners.add(listener)
    }

    override fun registerListener(listener: AllureStepsListener) {
        allureStepsListeners.add(listener)
    }

    private val stringBuffer = StringBuffer()
    private val stepsBuffer = StringBuffer()

    override fun onLine(line: String) {
        parseAllureSteps(line)
        stringBuffer.appendln(line)
    }

    override suspend fun testRunStarted(runName: String, testCount: Int) {
        device.addLogcatListener(this)
        stepsBuffer.setLength(0)
    }

    override suspend fun testEnded(test: TestIdentifier, testMetrics: Map<String, String>) {
        device.removeLogcatListener(this)

        val file = logWriter.saveLogs(test.toTest(), devicePoolId, device.toDeviceInfo(), listOf(stringBuffer.toString()))

        allureStepsListeners.forEach { it.onAllureSteps(test.toTest(), stepsResultConverter.fromJson(stepsBuffer.toString())) }
        attachmentListeners.forEach { it.onAttachment(test.toTest(), Attachment(file, AttachmentType.LOG)) }
    }

    override suspend fun testRunEnded(elapsedTime: Long, runMetrics: Map<String, String>) {
        device.removeLogcatListener(this)
    }

    override suspend fun testRunFailed(errorMessage: String) {
        device.removeLogcatListener(this)
    }


    private fun parseAllureSteps(line: String) {
        val stepsPrefixIndex = line.indexOf(ALLURE_STEPS_START_PREFIX)
        if (stepsPrefixIndex != -1) {
            val jsonSubstring = line.substring(stepsPrefixIndex + ALLURE_STEPS_START_PREFIX_LENGTH)
            stepsBuffer.append(jsonSubstring)
        }
    }

}
