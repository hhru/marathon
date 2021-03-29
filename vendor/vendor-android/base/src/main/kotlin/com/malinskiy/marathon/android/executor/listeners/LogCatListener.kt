package com.malinskiy.marathon.android.executor.listeners

import com.malinskiy.marathon.android.AndroidDevice
import com.malinskiy.marathon.android.executor.listeners.line.LineListener
import com.malinskiy.marathon.android.model.TestIdentifier
import com.malinskiy.marathon.device.DevicePoolId
import com.malinskiy.marathon.device.toDeviceInfo
import com.malinskiy.marathon.execution.Attachment
import com.malinskiy.marathon.execution.AttachmentType
import com.malinskiy.marathon.log.MarathonLogging
import com.malinskiy.marathon.report.attachment.AttachmentListener
import com.malinskiy.marathon.report.attachment.AttachmentProvider
import com.malinskiy.marathon.report.logs.LogWriter
import com.malinskiy.marathon.report.steps.AllureStepsListener
import com.malinskiy.marathon.report.steps.AllureStepsProvider
import com.malinskiy.marathon.report.steps.StepResultConverter
import java.util.concurrent.atomic.AtomicBoolean

class LogCatListener(
    private val device: AndroidDevice,
    private val devicePoolId: DevicePoolId,
    private val logWriter: LogWriter
) : NoOpTestRunListener(), AttachmentProvider, LineListener, AllureStepsProvider {

    companion object {
        private const val KASPRESSO_TAG = "I/KASPRESSO: "
        private const val KASPRESSO_TAG_LENGTH = KASPRESSO_TAG.length

        private const val ALLURE_STEPS_START_PREFIX = "#AllureStepsInfoJson#:"
        private const val ALLURE_STEPS_START_PREFIX_LENGTH = ALLURE_STEPS_START_PREFIX.length
    }

    private val attachmentListeners = mutableListOf<AttachmentListener>()
    private val allureStepsListeners = mutableListOf<AllureStepsListener>()

    private val stepsResultConverter = StepResultConverter()

    private val logger = MarathonLogging.logger("LogCatListener")

    private val stringBuffer = StringBuffer()
    private val stepsBuffer = StringBuffer()
    private val strangeOutputBuffer = StringBuffer()

    private val allureStepsJsonStarted = AtomicBoolean(false)


    override fun registerListener(listener: AttachmentListener) {
        attachmentListeners.add(listener)
    }

    override fun registerListener(listener: AllureStepsListener) {
        allureStepsListeners.add(listener)
    }


    override fun onLine(line: String) {
        parseAllureSteps2(line)
        stringBuffer.appendln(line)
    }

    override suspend fun testRunStarted(runName: String, testCount: Int) {
        device.addLogcatListener(this)
        stepsBuffer.setLength(0)
        strangeOutputBuffer.setLength(0)
        allureStepsJsonStarted.set(false)
    }

    override suspend fun testEnded(test: TestIdentifier, testMetrics: Map<String, String>) {
        device.removeLogcatListener(this)

        val file = logWriter.saveLogs(test.toTest(), devicePoolId, device.toDeviceInfo(), listOf(stringBuffer.toString()))

        val stepsJson = stepsBuffer.toString()
            .takeIf { it.isNotBlank() }
//            ?.replace("\\/", "/")
//            ?.fixUnicodeSymbols()
//            ?.replaceUnicodeSymbols()
            ?: "[]"
        val convertedStepsResults = stepsResultConverter.fromJson(
            testIdentifier = "${test.toTest()}",
            stepsResultJson = stepsJson
        ) ?: emptyList()

        allureStepsListeners.forEach { it.onAllureSteps(test.toTest(), convertedStepsResults) }
        attachmentListeners.forEach { it.onAttachment(test.toTest(), Attachment(file, AttachmentType.LOG)) }
    }

    override suspend fun testRunEnded(elapsedTime: Long, runMetrics: Map<String, String>) {
        device.removeLogcatListener(this)
    }

    override suspend fun testRunFailed(errorMessage: String) {
        device.removeLogcatListener(this)
    }


    private fun parseAllureSteps2(line: String) {
        val kaspressoTagIndex = line.indexOf(KASPRESSO_TAG)

        if (kaspressoTagIndex == -1) {
            if (allureStepsJsonStarted.get()) {
                if (line.contains("TestRunner: failed") || line.contains("TestRunner: finished")) {
                    allureStepsJsonStarted.set(false)
                } else {
                    logger.error { "Strange output: allure steps info expected" }
                }
            }
        } else {
            val kaspressoLogLine = line.substring(startIndex = kaspressoTagIndex + KASPRESSO_TAG_LENGTH)
            val allureStepsJsonIndex = kaspressoLogLine.indexOf(ALLURE_STEPS_START_PREFIX)

            if (allureStepsJsonIndex != -1) {
                allureStepsJsonStarted.set(true)

                val strangeLine = strangeOutputBuffer.toString().takeIf { it.isNotEmpty() } ?: ""
                strangeOutputBuffer.setLength(0)

                if (strangeLine.contains(KASPRESSO_TAG)) {
                    // skip line
                    logger.warn { "Skip '$strangeLine' for allure JSON" }
                } else {
                    stepsBuffer.append(strangeLine)
                }

                stepsBuffer.append(kaspressoLogLine.substring(startIndex = allureStepsJsonIndex + ALLURE_STEPS_START_PREFIX_LENGTH))
            } else {
                if (allureStepsJsonStarted.get()) {
                    strangeOutputBuffer.append(kaspressoLogLine)
                }
            }
        }
    }


    private fun parseAllureSteps(line: String) {
        val stepsPrefixIndex = line.indexOf(ALLURE_STEPS_START_PREFIX)
        if (stepsPrefixIndex != -1) {
            val jsonSubstring = line.substring(stepsPrefixIndex + ALLURE_STEPS_START_PREFIX_LENGTH)
            stepsBuffer.append(jsonSubstring)
        }
    }

    private fun String.fixUnicodeSymbols(): String {
        val mistakes = listOf(
            "\\ u003", "\\u 003", "\\u0 03", "\\u00 3", "\\u003 "
        )

        var result = this.replace(" u003", "u003")
        for (item in mistakes) {
            result = result.replace(item, "\\u003")
        }
        return result
    }

    private fun String.replaceUnicodeSymbols(): String {
        val mapping = mapOf(
            "\\u003c" to "<",
            "\\u003d" to "=",
            "\\u003e" to ">",
        )

        var result = this
        for (item in mapping) {
            result = result.replace(item.key, item.value)
        }
        return result
    }

}
