package com.malinskiy.marathon.steps

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.malinskiy.marathon.log.MarathonLogging
import com.malinskiy.marathon.steps.deserializers.AllureStageDeserializer
import com.malinskiy.marathon.steps.deserializers.AllureStatusDeserializer
import io.qameta.allure.model.Stage
import io.qameta.allure.model.Status
import io.qameta.allure.model.StepResult
import java.lang.reflect.Type

class StepResultConverter {

    private val stepsListType: Type by lazy { object : TypeToken<List<StepResult>>() {}.type }
    private val gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(Status::class.java, AllureStatusDeserializer())
            .registerTypeAdapter(Stage::class.java, AllureStageDeserializer())
            .create()
    }
    private val logger = MarathonLogging.logger("StepResultConverter")

    fun fromJson(stepsResultJson: String): List<StepResult> {
        return try {
            gson.fromJson(stepsResultJson, stepsListType)
        } catch (ex: JsonParseException) {
            logger.error(ex) { "Error with parsing steps json [json: $stepsResultJson]" }
            emptyList()
        }
    }

}