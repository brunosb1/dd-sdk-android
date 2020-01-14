package com.datadog.android.core.internal.lifecycle

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.impl.WorkManagerImpl
import com.datadog.android.core.internal.utils.UPLOAD_WORKER_TAG
import com.datadog.android.log.internal.net.NetworkInfo
import com.datadog.android.log.internal.net.NetworkInfoProvider
import com.datadog.android.utils.mockAppContext
import com.datadog.tools.unit.setFieldValue
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import java.lang.ref.WeakReference
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ProcessLifecycleCallbackTest {

    lateinit var mockContext: Context

    @Mock
    lateinit var mockWorkManager: WorkManagerImpl

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    lateinit var underTest: ProcessLifecycleCallback

    @BeforeEach
    fun `set up`() {
        mockContext = mockAppContext()
        underTest = ProcessLifecycleCallback(mockNetworkInfoProvider, mockContext)
    }

    @Test
    fun `when process stopped and network is disconnected will schedule an upload worker`() {
        // given
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockWorkManager)
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo())
            .thenReturn(NetworkInfo(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED))

        // when
        underTest.onStopped()

        // then
        verify(mockWorkManager)
            .enqueueUniqueWork(
                eq(UPLOAD_WORKER_TAG),
                eq(ExistingWorkPolicy.REPLACE),
                any<OneTimeWorkRequest>())
    }

    @Test
    fun `when process stopped and work manager is not present will not throw exception`() {
        // given
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo())
            .thenReturn(NetworkInfo(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED))

        // when
        underTest.onStopped()
    }

    @Test
    fun `when process stopped and network is connected will do nothing`() {
        // given
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockWorkManager)
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo())
            .thenReturn(NetworkInfo(NetworkInfo.Connectivity.NETWORK_WIFI))

        // when
        underTest.onStopped()

        // then
        verifyZeroInteractions(mockWorkManager)
    }

    @Test
    fun `when process stopped and context ref is null will do nothing`() {
        underTest.setFieldValue("contextWeakRef", WeakReference<Context>(null))
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockWorkManager)
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo())
            .thenReturn(NetworkInfo(NetworkInfo.Connectivity.NETWORK_WIFI))

        // when
        underTest.onStopped()

        // then
        verifyZeroInteractions(mockWorkManager)
    }
}
