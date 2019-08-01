package com.malinskiy.marathon.report.allure

import com.github.automatedowl.tools.AllureEnvironmentWriter.allureEnvironmentWriter
import com.google.common.collect.ImmutableMap
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.malinskiy.marathon.analytics.tracker.NoOpTracker
import com.malinskiy.marathon.device.DeviceInfo
import com.malinskiy.marathon.device.DevicePoolId
import com.malinskiy.marathon.execution.Configuration
import com.malinskiy.marathon.execution.TestResult
import com.malinskiy.marathon.execution.TestStatus
import com.malinskiy.marathon.log.MarathonLogging
import com.malinskiy.marathon.report.allure.deserializers.AllureStageDeserializer
import com.malinskiy.marathon.report.allure.deserializers.AllureStatusDeserializer
import com.malinskiy.marathon.test.Test
import com.malinskiy.marathon.test.toSimpleSafeTestName
import io.qameta.allure.AllureLifecycle
import io.qameta.allure.Description
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import io.qameta.allure.FileSystemResultsWriter
import io.qameta.allure.Issue
import io.qameta.allure.Owner
import io.qameta.allure.Severity
import io.qameta.allure.SeverityLevel
import io.qameta.allure.Story
import io.qameta.allure.TmsLink
import io.qameta.allure.model.*
import io.qameta.allure.util.ResultsUtils
import java.io.File
import java.lang.reflect.Type
import java.util.*

class AllureTestListener(val configuration: Configuration, val outputDirectory: File)
    : NoOpTracker() {

    private val logger = MarathonLogging.logger {}
    private val lifecycle: AllureLifecycle by lazy { AllureLifecycle(FileSystemResultsWriter(outputDirectory.toPath())) }
    private val stepsListType: Type by lazy { object : TypeToken<List<StepResult>>() {}.type }
    private val gson by lazy {
        GsonBuilder()
                .registerTypeAdapter(Status::class.java, AllureStatusDeserializer())
                .registerTypeAdapter(Stage::class.java, AllureStageDeserializer())
                .create()
    }


    override fun terminate() {
        val params = configuration.toMap()

        val builder = ImmutableMap.builder<String, String>()
        params.forEach {
            builder.put(it.key, it.value)
        }

        allureEnvironmentWriter(
                builder.build(), outputDirectory.absolutePath + File.separator)
    }

    override fun trackRawTestRun(poolId: DevicePoolId, device: DeviceInfo, testResult: TestResult) {
        val uuid = UUID.randomUUID().toString()
        val allureResults = createTestResult(uuid, device, testResult)
        lifecycle.scheduleTestCase(uuid, allureResults)
        lifecycle.writeTestCase(uuid)
    }

    private fun createTestResult(uuid: String, device: DeviceInfo, testResult: TestResult): io.qameta.allure.model.TestResult {
        val test = testResult.test
        val fullName = test.toSimpleSafeTestName()
        val suite = "${test.pkg}.${test.clazz}"

        val allureAttachments: List<Attachment> = testResult.attachments.map {
            Attachment()
                    .setName(it.type.name.toLowerCase().capitalize())
                    .setSource(it.file.absolutePath)
                    .setType(it.type.toMimeType())
        }

        var hasStepsJsonParsingError = false
        val allureSteps = try {
            testResult.stepsJson?.let { gson.fromJson<List<StepResult>>(it, stepsListType) } ?: listOf()
        } catch (ex: JsonParseException) {
            logger.error(ex, { "Error with parsing Allure steps json [json: ${testResult.stepsJson}]" })
            hasStepsJsonParsingError = true
            emptyList<StepResult>()
        }

        val status: Status = when {
            hasStepsJsonParsingError || testResult.status == TestStatus.FAILURE -> Status.FAILED
            testResult.status == TestStatus.PASSED -> Status.PASSED
            testResult.status == TestStatus.INCOMPLETE -> Status.BROKEN
            testResult.status == TestStatus.ASSUMPTION_FAILURE -> Status.SKIPPED
            testResult.status == TestStatus.IGNORED -> Status.SKIPPED
            else -> Status.SKIPPED
        }

        val allureTestResult = io.qameta.allure.model.TestResult()
                .setUuid(uuid)
                .setFullName(fullName)
                .setHistoryId(getHistoryId(test))
                .setStatus(status)
                .setStart(testResult.startTime)
                .setStop(testResult.endTime)
                .setAttachments(allureAttachments)
                .setSteps(allureSteps)
                .setParameters()
                .setLabels(
                        ResultsUtils.createHostLabel().setValue(device.serialNumber),
                        ResultsUtils.createPackageLabel(test.pkg),
                        ResultsUtils.createTestClassLabel(test.clazz),
                        ResultsUtils.createTestMethodLabel(test.method),
                        ResultsUtils.createSuiteLabel(suite)
                )

        testResult.stacktrace?.let {
            allureTestResult.setStatusDetails(
                    StatusDetails()
                            .setMessage(it.lines().first())
                            .setTrace(it)
            )
        }


        test.findValue<String>(Description::class.java.canonicalName)?.let { allureTestResult.setDescription(it) }
        test.findValue<String>(Issue::class.java.canonicalName)?.let { allureTestResult.links.add(it.toLink()) }
        test.findValue<String>(TmsLink::class.java.canonicalName)?.let { allureTestResult.links.add(it.toLink()) }

        allureTestResult.labels.addAll(test.getOptionalLabels())


        return allureTestResult
    }

    private fun getHistoryId(test: Test) =
            ResultsUtils.generateMethodSignatureHash(test.clazz, test.method, emptyList())

    private fun Test.getOptionalLabels(): Collection<Label> {
        val list = mutableListOf<Label>()

        findValue<String>(Epic::class.java.canonicalName)?.let { list.add(ResultsUtils.createEpicLabel(it)) }
        findValue<String>(Feature::class.java.canonicalName)?.let { list.add(ResultsUtils.createFeatureLabel(it)) }
        findValue<String>(Story::class.java.canonicalName)?.let { list.add(ResultsUtils.createStoryLabel(it)) }
        findValue<SeverityLevel>(Severity::class.java.canonicalName)?.let { list.add(ResultsUtils.createSeverityLabel(it)) }
        findValue<String>(Owner::class.java.canonicalName)?.let { list.add(ResultsUtils.createOwnerLabel(it)) }

        return list
    }

    private fun String.toLink(): io.qameta.allure.model.Link {
        return io.qameta.allure.model.Link().also {
            it.name = "Issue"
            it.url = this
        }
    }

    private inline fun <reified T> Test.findValue(name: String): T? {
        metaProperties.find { it.name == name }?.let { property ->
            return property.values["value"] as? T
        }

        return null
    }
}