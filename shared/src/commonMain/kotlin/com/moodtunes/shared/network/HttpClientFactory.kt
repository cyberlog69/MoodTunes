package com.moodtunes.shared.network

import io.ktor.client.HttpClient

expect fun createPlatformHttpClient(): HttpClient
