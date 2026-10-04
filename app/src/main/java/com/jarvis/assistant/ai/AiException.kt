package com.jarvis.assistant.ai

/**
 * Thrown by [GeminiClient] / [GroqClient] when a call fails.
 * code: HTTP status (429 = limit, 404 = model gone, 5xx = server), -1 = no network, -2 = unreadable answer.
 * [AssistantAi] catches it and silently tries the other provider.
 */
class AiException(val code: Int, message: String) : Exception(message)
