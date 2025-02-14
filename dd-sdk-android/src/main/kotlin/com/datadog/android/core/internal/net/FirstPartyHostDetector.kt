/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import okhttp3.HttpUrl
import java.util.Locale

internal class FirstPartyHostDetector(
    hosts: List<String>
) {

    internal var knownHosts = hosts.map { it.lowercase(Locale.US) }
        private set

    fun isFirstPartyUrl(url: HttpUrl): Boolean {
        val host = url.host()
        return knownHosts.any {
            it == "*" || host == it || host.endsWith(".$it")
        }
    }

    fun isFirstPartyUrl(url: String): Boolean {
        val httpUrl = HttpUrl.parse(url) ?: return false
        return isFirstPartyUrl(httpUrl)
    }

    fun isEmpty(): Boolean {
        return knownHosts.isEmpty()
    }

    fun addKnownHosts(hosts: List<String>) {
        knownHosts = knownHosts + hosts.map { it.lowercase(Locale.US) }
    }
}
