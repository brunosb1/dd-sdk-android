/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.trace

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.rules.MockServerActivityTestRule
import com.datadog.android.sdk.utils.isTracesUrl
import com.datadog.tools.unit.ConditionWatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class ConsentPendingNotGrantedTracesTest : TracesTest() {

    @get:Rule
    val mockServerRule = MockServerActivityTestRule(
        ActivityLifecycleTrace::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.PENDING
    )

    @Test
    fun verifyAllTracesAreDropped() {
        runInstrumentationScenario(mockServerRule)

        // update the tracking consent
        Datadog.setTrackingConsent(TrackingConsent.NOT_GRANTED)

        // Wait to make sure all batches are consumed
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        ConditionWatcher {
            val tracePayloads = mockServerRule
                .getRequests()
                .filter { it.url?.isTracesUrl() ?: false }
            assertThat(tracePayloads).isEmpty()
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }
}
