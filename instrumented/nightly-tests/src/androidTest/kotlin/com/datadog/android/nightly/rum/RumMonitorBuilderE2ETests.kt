/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.rum

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.tools.unit.invokeMethod
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class RumMonitorBuilderE2ETests {

    @get:Rule
    val forge = ForgeRule()

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    /**
     * apiMethodSignature: RumMonitor#Builder#fun sampleRumSessions(Float): Builder
     * apiMethodSignature: RumMonitor#Builder#fun build(): RumMonitor
     */
    @Test
    fun rum_rummonitor_builder_sample_all_in() {
        val testMethodName = "rum_rummonitor_builder_sample_all_in"

        initializeSdk(
            InstrumentationRegistry.getInstrumentation().targetContext,
            rumMonitorProvider = {
                measureRumMonitorInitialize {
                    RumMonitor.Builder().build()
                }
            }
        )

        sendRandomRumEvent(forge, testMethodName)
    }

    /**
     * apiMethodSignature: RumMonitor#Builder#fun sampleRumSessions(Float): Builder
     * apiMethodSignature: RumMonitor#Builder#fun build(): RumMonitor
     */
    @Test
    fun rum_rummonitor_builder_sample_all_out() {
        val testMethodName = "rum_rummonitor_builder_sample_all_out"
        initializeSdk(
            InstrumentationRegistry.getInstrumentation().targetContext,
            rumMonitorProvider = {
                measureRumMonitorInitialize {
                    RumMonitor.Builder().sampleRumSessions(0f).build()
                }
            }
        )
        sendRandomRumEvent(forge, testMethodName)
    }

    /**
     * apiMethodSignature: RumMonitor#Builder#fun sampleRumSessions(Float): Builder
     * apiMethodSignature: RumMonitor#Builder#fun build(): RumMonitor
     */
    @Test
    fun rum_rummonitor_builder_sample_in_75_percent() {
        val testMethodName = "rum_rummonitor_builder_sample_in_75_percent"
        val eventsNumber = 10
        initializeSdk(
            InstrumentationRegistry.getInstrumentation().targetContext,
            rumMonitorProvider = {
                measureRumMonitorInitialize {
                    RumMonitor.Builder().sampleRumSessions(75f).build()
                }
            }
        )
        repeat(eventsNumber) {
            sendRandomRumEvent(forge, testMethodName)
            // expire the session here
            GlobalRum.get().invokeMethod("resetSession")
        }
    }
}
