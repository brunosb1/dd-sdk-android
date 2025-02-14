/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing

import androidx.annotation.FloatRange
import com.datadog.android.DatadogInterceptor
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.sampling.RateBasedSampler
import com.datadog.android.core.internal.sampling.Sampler
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.utils.warningWithTelemetry
import com.datadog.android.tracing.internal.TracingFeature
import com.datadog.opentracing.DDTracer
import com.datadog.trace.api.DDTags
import com.datadog.trace.api.interceptor.MutableSpan
import com.datadog.trace.api.sampling.PrioritySampling
import io.opentracing.Span
import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMapExtractAdapter
import io.opentracing.propagation.TextMapInject
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.atomic.AtomicReference

/**
 * Provides automatic trace integration for [OkHttpClient] by way of the [Interceptor] system.
 *
 * This interceptor will create a [Span] around the request and fill the request information
 * (url, method, status code, optional error). It will also propagate the span and trace
 * information in the request header to link it with backend spans.
 *
 * If you use multiple Interceptors, make sure that this one is called first.
 * If you also want to track network requests as RUM Resources, use the
 * [DatadogInterceptor] instead, which combines the RUM and APM integrations.
 *
 * If you want to get more insights on the network requests (e.g.: redirections), you can also add
 * this interceptor as a Network level interceptor.
 *
 * To use:
 * ```
 *     val tracedHosts = listOf("example.com", "example.eu")
 *     val okHttpClient = OkHttpClient.Builder()
 *         .addInterceptor(TracingInterceptor(tracedHosts)))
 *         // Optionally to get information about redirections and retries
 *         // .addNetworkInterceptor(new TracingInterceptor(tracedHosts))
 *         .build();
 * ```
 *
 * @param tracedHosts a list of all the hosts that you want to be automatically tracked
 * by this interceptor. If no host is provided (via this argument or global
 * configuration [Configuration.Builder.setFirstPartyHosts]) the interceptor won't trace
 * any [okhttp3.Request], nor propagate tracing information to the backend.
 * @param tracedRequestListener a listener for automatically created [Span]s
 *
 */
@Suppress("StringLiteralDuplication")
open class TracingInterceptor
internal constructor(
    internal val tracedHosts: List<String>,
    internal val tracedRequestListener: TracedRequestListener,
    internal val firstPartyHostDetector: FirstPartyHostDetector,
    internal val traceOrigin: String?,
    internal val traceSampler: Sampler,
    internal val localTracerFactory: () -> Tracer
) : Interceptor {

    private val localTracerReference: AtomicReference<Tracer> = AtomicReference()

    private val localFirstPartyHostDetector = FirstPartyHostDetector(tracedHosts)

    init {
        if (localFirstPartyHostDetector.isEmpty() && firstPartyHostDetector.isEmpty()) {
            devLogger.w(WARNING_TRACING_NO_HOSTS)
        }
    }

    /**
     * Creates a [TracingInterceptor] to automatically create a trace around OkHttp [Request]s.
     *
     * @param tracedHosts a list of all the hosts that you want to be automatically tracked
     * by this interceptor. If no host is provided (via this argument or global
     * configuration [Configuration.Builder.setFirstPartyHosts]) the interceptor won't trace any OkHttp [Request],
     * nor propagate tracing information to the backend.
     * @param tracedRequestListener a listener for automatically created [Span]s
     * @param traceSamplingRate the sampling rate for APM traces created for auto-instrumented
     * requests. It must be a value between `0.0` and `100.0`. A value of `0.0` means no trace will
     * be kept, `100.0` means all traces will be kept (default value is `20.0`).
     */
    @JvmOverloads
    constructor(
        tracedHosts: List<String>,
        tracedRequestListener: TracedRequestListener = NoOpTracedRequestListener(),
        @FloatRange(from = 0.0, to = 100.0) traceSamplingRate: Float = DEFAULT_TRACE_SAMPLING_RATE
    ) : this(
        tracedHosts,
        tracedRequestListener,
        CoreFeature.firstPartyHostDetector,
        null,
        RateBasedSampler(traceSamplingRate / 100),
        { AndroidTracer.Builder().build() }
    )

    /**
     * Creates a [TracingInterceptor] to automatically create a trace around OkHttp [Request]s.
     *
     * @param tracedRequestListener a listener for automatically created [Span]s
     * @param traceSamplingRate the sampling rate for APM traces created for auto-instrumented
     * requests. It must be a value between `0.0` and `100.0`. A value of `0.0` means no trace will
     * be kept, `100.0` means all traces will be kept (default value is `20.0`).
     */
    @JvmOverloads
    constructor(
        tracedRequestListener: TracedRequestListener = NoOpTracedRequestListener(),
        @FloatRange(from = 0.0, to = 100.0) traceSamplingRate: Float = DEFAULT_TRACE_SAMPLING_RATE
    ) : this(
        emptyList(),
        tracedRequestListener,
        CoreFeature.firstPartyHostDetector,
        null,
        RateBasedSampler(traceSamplingRate / 100),
        { AndroidTracer.Builder().build() }
    )

    // region Interceptor

    /** @inheritdoc */
    override fun intercept(chain: Interceptor.Chain): Response {
        val tracer = resolveTracer()
        val request = chain.request()

        return if (tracer == null || !isRequestTraceable(request)) {
            intercept(chain, request)
        } else {
            interceptAndTrace(chain, request, tracer)
        }
    }

    // endregion

    // region TracingInterceptor

    /**
     * Called whenever a span was successfully created around an OkHttp [Request].
     * The given [Span] can be updated (e.g.: add custom tags / baggage items) before it is
     * finalized.
     * @param request the intercepted [Request]
     * @param span the [Span] created around the [Request] (or null if request is not traced)
     * @param response the [Request] response (or null if an error occurred)
     * @param throwable the error which occurred during the [Request] (or null)
     */
    protected open fun onRequestIntercepted(
        request: Request,
        span: Span?,
        response: Response?,
        throwable: Throwable?
    ) {
        if (span != null) {
            tracedRequestListener.onRequestIntercepted(request, span, response, throwable)
        }
    }

    /**
     * @return whether the span can be sent to Datadog.
     */
    internal open fun canSendSpan(): Boolean {
        return true
    }

    // endregion

    // region Internal

    private fun isRequestTraceable(request: Request): Boolean {
        val url = request.url()
        return firstPartyHostDetector.isFirstPartyUrl(url) ||
            localFirstPartyHostDetector.isFirstPartyUrl(url)
    }

    @Suppress("TooGenericExceptionCaught", "ThrowingInternalException")
    private fun interceptAndTrace(
        chain: Interceptor.Chain,
        request: Request,
        tracer: Tracer
    ): Response {
        val isSampled = extractSamplingDecision(request) ?: traceSampler.sample()
        val span = if (isSampled) {
            buildSpan(tracer, request)
        } else {
            null
        }

        val updatedRequest = try {
            updateRequest(request, tracer, span).build()
        } catch (e: IllegalStateException) {
            sdkLogger.warningWithTelemetry("Failed to update intercepted OkHttp request", e)
            request
        }

        try {
            val response = chain.proceed(updatedRequest)
            handleResponse(request, response, span)
            return response
        } catch (e: Throwable) {
            handleThrowable(request, e, span)
            throw e
        }
    }

    @Suppress("TooGenericExceptionCaught", "ThrowingInternalException")
    private fun intercept(
        chain: Interceptor.Chain,
        request: Request
    ): Response {
        try {
            val response = chain.proceed(request)
            onRequestIntercepted(request, null, response, null)
            return response
        } catch (e: Throwable) {
            onRequestIntercepted(request, null, null, e)
            throw e
        }
    }

    @Synchronized
    private fun resolveTracer(): Tracer? {
        return if (!TracingFeature.initialized.get()) {
            devLogger.w(WARNING_TRACING_DISABLED)
            null
        } else if (GlobalTracer.isRegistered()) {
            // clear the localTracer reference if any
            localTracerReference.set(null)
            GlobalTracer.get()
        } else {
            // we check if we already have a local tracer if not we instantiate one
            resolveLocalTracer()
        }
    }

    private fun resolveLocalTracer(): Tracer {
        // only register once
        if (localTracerReference.get() == null) {
            @Suppress("UnsafeThirdPartyFunctionCall") // internal safe call
            localTracerReference.compareAndSet(null, localTracerFactory())
            devLogger.w(WARNING_DEFAULT_TRACER)
        }
        return localTracerReference.get()
    }

    private fun buildSpan(tracer: Tracer, request: Request): Span {
        val parentContext = extractParentContext(tracer, request)
        val url = request.url().toString()

        val spanBuilder = tracer.buildSpan(SPAN_NAME)
        (spanBuilder as? DDTracer.DDSpanBuilder)?.withOrigin(traceOrigin)
        val span = spanBuilder
            .asChildOf(parentContext)
            .start()

        (span as? MutableSpan)?.resourceName = url.substringBefore(URL_QUERY_PARAMS_BLOCK_SEPARATOR)
        span.setTag(Tags.HTTP_URL.key, url)
        span.setTag(Tags.HTTP_METHOD.key, request.method())

        return span
    }

    private fun extractSamplingDecision(request: Request): Boolean? {
        val samplingPriority = request.header(SAMPLING_PRIORITY_HEADER)?.toIntOrNull()
            ?: return null
        if (samplingPriority == PrioritySampling.UNSET) return null
        return samplingPriority == PrioritySampling.USER_KEEP ||
            samplingPriority == PrioritySampling.SAMPLER_KEEP
    }

    private fun extractParentContext(tracer: Tracer, request: Request): SpanContext? {
        val tagContext = request.tag(Span::class.java)?.context()

        val headerContext = tracer.extract(
            Format.Builtin.TEXT_MAP_EXTRACT,
            TextMapExtractAdapter(
                request.headers().toMultimap()
                    .map { it.key to it.value.joinToString(";") }
                    .toMap()
            )
        )

        return headerContext ?: tagContext
    }

    private fun updateRequest(
        request: Request,
        tracer: Tracer,
        span: Span?
    ): Request.Builder {
        val tracedRequestBuilder = request.newBuilder()

        if (span == null) {
            listOf(
                SAMPLING_PRIORITY_HEADER,
                TRACE_ID_HEADER,
                SPAN_ID_HEADER
            ).forEach {
                tracedRequestBuilder.removeHeader(it)
            }
            tracedRequestBuilder.addHeader(SAMPLING_PRIORITY_HEADER, DROP_SAMPLING_DECISION)
        } else {
            tracer.inject(
                span.context(),
                Format.Builtin.TEXT_MAP_INJECT,
                TextMapInject { key, value ->
                    // By default the `addHeader` method adds a value and doesn't replace it
                    // We need to remove the old trace/span info to use the one for the current span
                    tracedRequestBuilder.removeHeader(key)
                    tracedRequestBuilder.addHeader(key, value)
                }
            )
        }

        return tracedRequestBuilder
    }

    private fun handleResponse(
        request: Request,
        response: Response,
        span: Span?
    ) {
        if (span == null) {
            onRequestIntercepted(request, null, response, null)
        } else {
            val statusCode = response.code()
            span.setTag(Tags.HTTP_STATUS.key, statusCode)
            if (statusCode in 400..499) {
                (span as? MutableSpan)?.isError = true
            }
            if (statusCode == 404) {
                (span as? MutableSpan)?.resourceName = RESOURCE_NAME_404
            }
            onRequestIntercepted(request, span, response, null)
            if (canSendSpan()) {
                span.finish()
            } else {
                (span as? MutableSpan)?.drop()
            }
        }
    }

    private fun handleThrowable(
        request: Request,
        throwable: Throwable,
        span: Span?
    ) {
        if (span == null) {
            onRequestIntercepted(request, null, null, throwable)
        } else {
            (span as? MutableSpan)?.isError = true
            span.setTag(DDTags.ERROR_MSG, throwable.message)
            span.setTag(DDTags.ERROR_TYPE, throwable.javaClass.name)
            span.setTag(DDTags.ERROR_STACK, throwable.loggableStackTrace())
            onRequestIntercepted(request, span, null, throwable)
            if (canSendSpan()) {
                span.finish()
            } else {
                (span as? MutableSpan)?.drop()
            }
        }
    }

    // endregion

    companion object {
        internal const val SPAN_NAME = "okhttp.request"

        internal const val RESOURCE_NAME_404 = "404"

        internal const val HEADER_CT = "Content-Type"
        internal const val URL_QUERY_PARAMS_BLOCK_SEPARATOR = '?'

        internal const val WARNING_TRACING_NO_HOSTS =
            "You added a TracingInterceptor to your OkHttpClient, " +
                "but you did not specify any first party hosts. " +
                "Your requests won't be traced.\n" +
                "To set a list of known hosts, you can use the " +
                "Configuration.Builder::setFirstPartyHosts() method."
        internal const val WARNING_TRACING_DISABLED =
            "You added a TracingInterceptor to your OkHttpClient, " +
                "but you did not enable the TracingFeature. " +
                "Your requests won't be traced."
        internal const val WARNING_DEFAULT_TRACER =
            "You added a TracingInterceptor to your OkHttpClient, " +
                "but you didn't register any Tracer. " +
                "We automatically created a local tracer for you."

        internal const val DEFAULT_TRACE_SAMPLING_RATE: Float = 20f

        // taken from DatadogHttpCodec
        internal const val TRACE_ID_HEADER = "x-datadog-trace-id"
        internal const val SPAN_ID_HEADER = "x-datadog-parent-id"
        internal const val SAMPLING_PRIORITY_HEADER = "x-datadog-sampling-priority"

        internal const val DROP_SAMPLING_DECISION = "0"
    }
}
