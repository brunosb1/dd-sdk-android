/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.core.internal.utils.hasUserData
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.event.RumEventSourceProvider
import com.datadog.android.rum.model.ActionEvent
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal class RumActionScope(
    val parentScope: RumScope,
    val waitForStop: Boolean,
    eventTime: Time,
    initialType: RumActionType,
    initialName: String,
    initialAttributes: Map<String, Any?>,
    serverTimeOffsetInMs: Long,
    inactivityThresholdMs: Long = ACTION_INACTIVITY_MS,
    maxDurationMs: Long = ACTION_MAX_DURATION_MS,
    private val rumEventSourceProvider: RumEventSourceProvider,
    private val androidInfoProvider: AndroidInfoProvider,
    private val trackFrustrations: Boolean
) : RumScope {

    private val inactivityThresholdNs = TimeUnit.MILLISECONDS.toNanos(inactivityThresholdMs)
    private val maxDurationNs = TimeUnit.MILLISECONDS.toNanos(maxDurationMs)

    internal val eventTimestamp = eventTime.timestamp + serverTimeOffsetInMs
    internal val actionId: String = UUID.randomUUID().toString()
    internal var type: RumActionType = initialType
    internal var name: String = initialName
    private val startedNanos: Long = eventTime.nanoTime
    private var lastInteractionNanos: Long = startedNanos
    val networkInfo = CoreFeature.networkInfoProvider.getLatestNetworkInfo()

    internal val attributes: MutableMap<String, Any?> = initialAttributes.toMutableMap().apply {
        putAll(GlobalRum.globalAttributes)
    }

    private val ongoingResourceKeys = mutableListOf<WeakReference<Any>>()

    internal var resourceCount: Long = 0
    internal var errorCount: Long = 0
    internal var crashCount: Long = 0
    internal var longTaskCount: Long = 0

    private var sent = false
    internal var stopped = false

    // endregion

    override fun handleEvent(event: RumRawEvent, writer: DataWriter<Any>): RumScope? {
        val now = event.eventTime.nanoTime
        val isInactive = now - lastInteractionNanos > inactivityThresholdNs
        val isLongDuration = now - startedNanos > maxDurationNs
        ongoingResourceKeys.removeAll { it.get() == null }
        val isOngoing = waitForStop && !stopped
        val shouldStop = isInactive && ongoingResourceKeys.isEmpty() && !isOngoing

        when {
            shouldStop -> sendAction(lastInteractionNanos, writer)
            isLongDuration -> sendAction(now, writer)
            event is RumRawEvent.SendCustomActionNow -> sendAction(lastInteractionNanos, writer)
            event is RumRawEvent.StartView -> onStartView(now, writer)
            event is RumRawEvent.StopView -> onStopView(now, writer)
            event is RumRawEvent.StopAction -> onStopAction(event, now)
            event is RumRawEvent.StartResource -> onStartResource(event, now)
            event is RumRawEvent.StopResource -> onStopResource(event, now)
            event is RumRawEvent.AddError -> onError(event, now, writer)
            event is RumRawEvent.StopResourceWithError -> onResourceError(event.key, now)
            event is RumRawEvent.StopResourceWithStackTrace -> onResourceError(event.key, now)
            event is RumRawEvent.AddLongTask -> onLongTask(now)
        }

        return if (sent) null else this
    }

    override fun getRumContext(): RumContext {
        return parentScope.getRumContext()
    }

    override fun isActive(): Boolean {
        return !stopped
    }

    // endregion

    // region Internal

    private fun onStartView(
        now: Long,
        writer: DataWriter<Any>
    ) {
        // another view starts, complete this action
        ongoingResourceKeys.clear()
        sendAction(now, writer)
    }

    private fun onStopView(
        now: Long,
        writer: DataWriter<Any>
    ) {
        ongoingResourceKeys.clear()
        sendAction(now, writer)
    }

    private fun onStopAction(
        event: RumRawEvent.StopAction,
        now: Long
    ) {
        event.type?.let { type = it }
        event.name?.let { name = it }
        attributes.putAll(event.attributes)
        stopped = true
        lastInteractionNanos = now
    }

    private fun onStartResource(
        event: RumRawEvent.StartResource,
        now: Long
    ) {
        lastInteractionNanos = now
        resourceCount++
        ongoingResourceKeys.add(WeakReference(event.key))
    }

    private fun onStopResource(
        event: RumRawEvent.StopResource,
        now: Long
    ) {
        val keyRef = ongoingResourceKeys.firstOrNull { it.get() == event.key }
        if (keyRef != null) {
            ongoingResourceKeys.remove(keyRef)
            lastInteractionNanos = now
        }
    }

    private fun onError(
        event: RumRawEvent.AddError,
        now: Long,
        writer: DataWriter<Any>
    ) {
        lastInteractionNanos = now
        errorCount++

        if (event.isFatal) {
            crashCount++

            sendAction(now, writer)
        }
    }

    private fun onResourceError(eventKey: String, now: Long) {
        val keyRef = ongoingResourceKeys.firstOrNull { it.get() == eventKey }
        if (keyRef != null) {
            ongoingResourceKeys.remove(keyRef)
            lastInteractionNanos = now
            resourceCount--
            errorCount++
        }
    }

    private fun onLongTask(now: Long) {
        lastInteractionNanos = now
        longTaskCount++
    }

    @Suppress("LongMethod", "ComplexMethod")
    private fun sendAction(
        endNanos: Long,
        writer: DataWriter<Any>
    ) {
        if (sent) return

        val actualType = type
        attributes.putAll(GlobalRum.globalAttributes)

        val context = getRumContext()
        val user = CoreFeature.userInfoProvider.getUserInfo()
        val frustrations = mutableListOf<ActionEvent.Type>()
        if (trackFrustrations && errorCount > 0 && actualType == RumActionType.TAP) {
            frustrations.add(ActionEvent.Type.ERROR_TAP)
        }

        val actionEvent = ActionEvent(
            date = eventTimestamp,
            action = ActionEvent.ActionEventAction(
                type = actualType.toSchemaType(),
                id = actionId,
                target = ActionEvent.ActionEventActionTarget(name),
                error = ActionEvent.Error(errorCount),
                crash = ActionEvent.Crash(crashCount),
                longTask = ActionEvent.LongTask(longTaskCount),
                resource = ActionEvent.Resource(resourceCount),
                loadingTime = max(endNanos - startedNanos, 1L),
                frustration = if (frustrations.isEmpty()) {
                    null
                } else {
                    ActionEvent.Frustration(frustrations)
                }
            ),
            view = ActionEvent.View(
                id = context.viewId.orEmpty(),
                name = context.viewName,
                url = context.viewUrl.orEmpty()
            ),
            application = ActionEvent.Application(context.applicationId),
            session = ActionEvent.ActionEventSession(
                id = context.sessionId,
                type = ActionEvent.ActionEventSessionType.USER
            ),
            source = rumEventSourceProvider.actionEventSource,
            usr = if (!user.hasUserData()) {
                null
            } else {
                ActionEvent.Usr(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    additionalProperties = user.additionalProperties
                )
            },
            os = ActionEvent.Os(
                name = androidInfoProvider.osName,
                version = androidInfoProvider.osVersion,
                versionMajor = androidInfoProvider.osMajorVersion
            ),
            device = ActionEvent.Device(
                type = androidInfoProvider.deviceType.toActionSchemaType(),
                name = androidInfoProvider.deviceName,
                model = androidInfoProvider.deviceModel,
                brand = androidInfoProvider.deviceBrand,
                architecture = androidInfoProvider.architecture
            ),
            context = ActionEvent.Context(additionalProperties = attributes),
            dd = ActionEvent.Dd(session = ActionEvent.DdSession(plan = ActionEvent.Plan.PLAN_1)),
            connectivity = networkInfo.toActionConnectivity(),
            service = CoreFeature.serviceName,
            version = CoreFeature.packageVersionProvider.version
        )
        writer.write(actionEvent)

        sent = true
    }

    // endregion

    companion object {
        internal const val ACTION_INACTIVITY_MS = 100L
        internal const val ACTION_MAX_DURATION_MS = 5000L

        @Suppress("LongParameterList")
        fun fromEvent(
            parentScope: RumScope,
            event: RumRawEvent.StartAction,
            timestampOffset: Long,
            eventSourceProvider: RumEventSourceProvider,
            androidInfoProvider: AndroidInfoProvider,
            trackFrustrations: Boolean
        ): RumScope {
            return RumActionScope(
                parentScope,
                event.waitForStop,
                event.eventTime,
                event.type,
                event.name,
                event.attributes,
                timestampOffset,
                rumEventSourceProvider = eventSourceProvider,
                androidInfoProvider = androidInfoProvider,
                trackFrustrations = trackFrustrations
            )
        }
    }
}
