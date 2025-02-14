/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.utils.errorWithTelemetry
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.util.Locale

internal class RumEventDeserializer : Deserializer<Any> {

    // region Deserializer

    override fun deserialize(model: String): Any? {
        return try {
            val jsonObject = JsonParser.parseString(model).asJsonObject
            parseEvent(
                jsonObject.getAsJsonPrimitive(EVENT_TYPE_KEY_NAME)?.asString,
                model,
                jsonObject
            )
        } catch (e: JsonParseException) {
            sdkLogger.errorWithTelemetry(
                DESERIALIZE_ERROR_MESSAGE_FORMAT.format(Locale.US, model),
                e
            )
            null
        } catch (e: IllegalStateException) {
            sdkLogger.errorWithTelemetry(
                DESERIALIZE_ERROR_MESSAGE_FORMAT.format(Locale.US, model),
                e
            )
            null
        }
    }

    // endregion

    // region Internal

    @SuppressWarnings("ThrowingInternalException")
    @Throws(JsonParseException::class)
    private fun parseEvent(eventType: String?, model: String, modelAsJson: JsonObject): Any {
        return when (eventType) {
            EVENT_TYPE_VIEW -> ViewEvent.fromJson(model)
            EVENT_TYPE_RESOURCE -> ResourceEvent.fromJson(model)
            EVENT_TYPE_ACTION -> ActionEvent.fromJson(model)
            EVENT_TYPE_ERROR -> ErrorEvent.fromJson(model)
            EVENT_TYPE_LONG_TASK -> LongTaskEvent.fromJson(model)
            EVENT_TYPE_TELEMETRY -> {
                val status = modelAsJson
                    .getAsJsonObject(EVENT_TELEMETRY_KEY_NAME)
                    .getAsJsonPrimitive(EVENT_TELEMETRY_STATUS_KEY_NAME)
                    .asString
                when (status) {
                    TELEMETRY_TYPE_DEBUG -> TelemetryDebugEvent.fromJson(model)
                    TELEMETRY_TYPE_ERROR -> TelemetryErrorEvent.fromJson(model)
                    else -> throw JsonParseException(
                        "We could not deserialize the telemetry event with status: $status"
                    )
                }
            }
            else -> throw JsonParseException(
                "We could not deserialize the event with type: $eventType"
            )
        }
    }

    // endregion

    companion object {
        const val EVENT_TYPE_KEY_NAME = "type"
        const val EVENT_TELEMETRY_KEY_NAME = "telemetry"
        const val EVENT_TELEMETRY_STATUS_KEY_NAME = "status"

        const val EVENT_TYPE_VIEW = "view"
        const val EVENT_TYPE_RESOURCE = "resource"
        const val EVENT_TYPE_ACTION = "action"
        const val EVENT_TYPE_ERROR = "error"
        const val EVENT_TYPE_LONG_TASK = "long_task"
        const val EVENT_TYPE_TELEMETRY = "telemetry"

        const val TELEMETRY_TYPE_DEBUG = "debug"
        const val TELEMETRY_TYPE_ERROR = "error"

        const val DESERIALIZE_ERROR_MESSAGE_FORMAT =
            "Error while trying to deserialize the serialized RumEvent: %s"
    }
}
