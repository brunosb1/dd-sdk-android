/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import java.net.URL
import java.util.UUID

internal fun Forge.interactiveRumRawEvent(): RumRawEvent {
    return anElementFrom(
        startViewEvent(),
        startActionEvent()
    )
}

internal fun Forge.startViewEvent(): RumRawEvent.StartView {
    return RumRawEvent.StartView(
        key = anHexadecimalString(),
        name = anAlphabeticalString(),
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.startActionEvent(continuous: Boolean? = null): RumRawEvent.StartAction {
    return RumRawEvent.StartAction(
        type = aValueFrom(RumActionType::class.java),
        name = anAlphabeticalString(),
        waitForStop = continuous ?: aBool(),
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.stopActionEvent(): RumRawEvent.StopAction {
    return RumRawEvent.StopAction(
        type = aValueFrom(RumActionType::class.java),
        name = anAlphabeticalString(),
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.startResourceEvent(): RumRawEvent.StartResource {
    return RumRawEvent.StartResource(
        key = anAlphabeticalString(),
        url = getForgery<URL>().toString(),
        method = anElementFrom("POST", "GET", "PUT", "DELETE", "HEAD"),
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.stopResourceEvent(): RumRawEvent.StopResource {
    return RumRawEvent.StopResource(
        key = anAlphabeticalString(),
        statusCode = aNullable { aLong(100, 600) },
        size = aNullable { aPositiveLong() },
        kind = aValueFrom(RumResourceKind::class.java),
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.stopResourceWithErrorEvent(): RumRawEvent.StopResourceWithError {
    return RumRawEvent.StopResourceWithError(
        key = anAlphabeticalString(),
        statusCode = aNullable { aLong(100, 600) },
        source = aValueFrom(RumErrorSource::class.java),
        message = anAlphabeticalString(),
        throwable = aThrowable(),
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.stopResourceWithStacktraceEvent(): RumRawEvent.StopResourceWithStackTrace {
    return RumRawEvent.StopResourceWithStackTrace(
        key = anAlphabeticalString(),
        statusCode = aNullable { aLong(100, 600) },
        source = aValueFrom(RumErrorSource::class.java),
        message = anAlphabeticalString(),
        stackTrace = anAlphabeticalString(),
        errorType = aNullable { anAlphabeticalString() },
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.addErrorEvent(): RumRawEvent.AddError {
    return RumRawEvent.AddError(
        message = anAlphabeticalString(),
        source = aValueFrom(RumErrorSource::class.java),
        stacktrace = null,
        throwable = null,
        isFatal = this.aBool(),
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.addLongTaskEvent(): RumRawEvent.AddLongTask {
    return RumRawEvent.AddLongTask(
        durationNs = aLong(min = 1),
        target = anAlphabeticalString()
    )
}

internal fun Forge.validBackgroundEvent(): RumRawEvent {
    return this.anElementFrom(
        listOf(
            startActionEvent(),
            addErrorEvent(),
            startResourceEvent()
        )
    )
}

internal fun Forge.invalidBackgroundEvent(): RumRawEvent {
    return this.anElementFrom(
        listOf(
            addLongTaskEvent(),
            stopActionEvent(),
            stopResourceEvent(),
            stopResourceWithErrorEvent(),
            stopResourceWithStacktraceEvent()
        )
    )
}

internal fun Forge.validAppLaunchEvent(): RumRawEvent {
    return this.anElementFrom(
        listOf(
            startActionEvent(),
            addLongTaskEvent(),
            addErrorEvent(),
            startResourceEvent()
        )
    )
}

internal fun Forge.invalidAppLaunchEvent(): RumRawEvent {
    return this.anElementFrom(
        listOf(
            stopActionEvent(),
            stopResourceEvent(),
            stopResourceWithErrorEvent(),
            stopResourceWithStacktraceEvent()
        )
    )
}

internal fun Forge.silentOrphanEvent(): RumRawEvent {
    val fakeId = getForgery<UUID>().toString()

    return this.anElementFrom(
        listOf(
            RumRawEvent.ApplicationStarted(Time(), aLong()),
            RumRawEvent.ResetSession(),
            RumRawEvent.KeepAlive(),
            RumRawEvent.StopView(anAlphabeticalString(), emptyMap()),
            RumRawEvent.ActionSent(fakeId, aPositiveInt()),
            RumRawEvent.ErrorSent(fakeId),
            RumRawEvent.LongTaskSent(fakeId),
            RumRawEvent.ResourceSent(fakeId),
            RumRawEvent.ActionDropped(fakeId),
            RumRawEvent.ErrorDropped(fakeId),
            RumRawEvent.LongTaskDropped(fakeId),
            RumRawEvent.ResourceDropped(fakeId)
        )
    )
}
