package com.steady.app.data

import com.steady.app.domain.AdjustmentKind
import com.steady.app.domain.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * The only two places a model is allowed to touch this product, and they have different
 * shapes on purpose.
 *
 * [narrateSummary] returns prose, because a weekly note to a family member is prose.
 * [SummaryGuard] checks every one before it is shown.
 *
 * [chooseAdjustmentPhrasing] returns **an index**, because its answer appears on the
 * exercise screen in front of somebody who is about to stand up. The sentences it picks
 * between are this app's own (see
 * [AdjustmentPhrasings][com.steady.app.domain.AdjustmentPhrasings]), so there is nothing
 * to validate: no string crosses this boundary in that direction at all.
 *
 * Every implementation returns null rather than throwing when the model is unreachable,
 * so the caller always has a deterministic fallback ready.
 */
interface Narrator {
    suspend fun narrateSummary(facts: SummaryFacts): String?
    suspend fun chooseAdjustmentPhrasing(request: PhrasingRequest): Int?
}

/** What the model is told when it picks a sentence: which kind of change happened, the
 * rules' own description of it, and the numbered list it must choose from. */
data class PhrasingRequest(
    val kind: AdjustmentKind,
    val adjustmentDescription: String,
    val options: List<String>,
)

data class SummaryFacts(
    val familyMemberName: String,
    val weekNumber: Int,
    val sessionsCompletedThisWeek: Int,
    val prescribedPerWeek: Int,
    val streakWeeks: Int,
    val tier: Tier,
    /**
     * Days between the most recent completed session and the day this summary is being
     * written, or null when nothing has ever been completed.
     *
     * This is the fact that lets the weekly update finally carry a session that did not
     * happen, and it is here rather than in the prompt's prose for a reason
     * the shared guard-wording standard's §1 is explicit about: the app computes it
     * ([AdherenceTracker.daysSinceLastSession][com.steady.app.domain.AdherenceTracker.daysSinceLastSession]),
     * the app writes the sentence
     * ([CaregiverSummaryBuilder][com.steady.app.domain.CaregiverSummaryBuilder]), and
     * the model is only ever told the number. A model that authors the observation
     * "she has been unsteady since Thursday" is the failure this whole boundary exists
     * to prevent, and a model that authors "she has not exercised in a week" is the
     * same failure wearing an adherence hat.
     */
    val daysSinceLastSession: Int? = null,
)

/** Calls the local Bedrock proxy (see `backend/bedrock_proxy.py`) over plain HTTP.
 * The proxy, not this app, holds the AWS credentials: an Android client that embedded a
 * long-lived AWS secret key would be shipping a real vulnerability, not a shortcut.
 * `10.0.2.2` is the standard Android-emulator alias for the host machine's loopback
 * interface, so this reaches the proxy running on the same development machine. A real
 * deployment would point this at a small managed backend instead; the contract (POST
 * facts, get prose back, time out fast) would not change. */
class BedrockHttpNarrator(
    private val baseUrl: String = "http://10.0.2.2:8765",
    private val timeoutMillis: Long = 4000,
) : Narrator {

    override suspend fun narrateSummary(facts: SummaryFacts): String? {
        val json = buildString {
            append('{')
            appendJsonString("familyMemberName", facts.familyMemberName)
            append(',')
            appendJsonNumber("weekNumber", facts.weekNumber)
            append(',')
            appendJsonNumber("sessionsCompletedThisWeek", facts.sessionsCompletedThisWeek)
            append(',')
            appendJsonNumber("prescribedPerWeek", facts.prescribedPerWeek)
            append(',')
            appendJsonNumber("streakWeeks", facts.streakWeeks)
            append(',')
            // Sent only when there is one. A key that is absent says "nothing has ever
            // been recorded"; a key that is zero says "there was one today", and those
            // must not collapse into each other on the wire.
            facts.daysSinceLastSession?.let {
                appendJsonNumber("daysSinceLastSession", it)
                append(',')
            }
            appendJsonString("tier", facts.tier.name, trailingComma = false)
            append('}')
        }
        return postForText("/summarize", json)
    }

    override suspend fun chooseAdjustmentPhrasing(request: PhrasingRequest): Int? {
        val json = buildString {
            append('{')
            appendJsonString("kind", request.kind.name)
            append(',')
            appendJsonString("adjustmentDescription", request.adjustmentDescription)
            append(',')
            append("\"options\":[")
            request.options.forEachIndexed { index, option ->
                if (index > 0) append(',')
                append('"').append(jsonEscape(option)).append('"')
            }
            append(']')
            append('}')
        }
        return postForBody("/choose-explanation", json)?.let(::extractChoiceField)
    }

    private suspend fun postForText(path: String, jsonBody: String): String? =
        postForBody(path, jsonBody)?.let(::extractTextField)

    private suspend fun postForBody(path: String, jsonBody: String): String? {
        return withTimeoutOrNull(timeoutMillis) {
            withContext(Dispatchers.IO) {
                try {
                    val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = timeoutMillis.toInt()
                        readTimeout = timeoutMillis.toInt()
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    connection.outputStream.use { it.write(jsonBody.toByteArray(StandardCharsets.UTF_8)) }
                    val code = connection.responseCode
                    if (code !in 200..299) return@withContext null
                    connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                } catch (io: IOException) {
                    null
                }
            }
        }
    }
}

/** Deterministic (no network, no randomness) fallback used whenever the model is
 * unreachable or slow. Callers should treat a null [Narrator] result as "use this
 * instead," never as an error to surface to the person on the television. */
object FallbackNarrator : Narrator {
    override suspend fun narrateSummary(facts: SummaryFacts): String? = null
    override suspend fun chooseAdjustmentPhrasing(request: PhrasingRequest): Int? = null
}

private fun StringBuilder.appendJsonString(key: String, value: String, trailingComma: Boolean = true) {
    append('"').append(key).append("\":\"").append(jsonEscape(value)).append('"')
}

private fun StringBuilder.appendJsonNumber(key: String, value: Int) {
    append('"').append(key).append("\":").append(value)
}

private fun jsonEscape(raw: String): String = raw
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "")

private val TEXT_FIELD_PATTERN: Pattern = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")

/** A hand-rolled extractor for exactly the one field this app's own proxy ever returns.
 * Both ends of this contract are owned by this app, so a tiny regex-based reader avoids
 * pulling in a general JSON dependency for a single known field, without weakening the
 * "mock at the API boundary" rule: the boundary here is the proxy's HTTP response, and
 * this reads it honestly rather than assuming a shape it did not check. */
internal fun extractTextField(json: String): String? {
    val matcher = TEXT_FIELD_PATTERN.matcher(json)
    if (!matcher.find()) return null
    val raw = matcher.group(1) ?: return null
    val unescaped = raw
        .replace("\\n", "\n")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
    return unescaped.ifBlank { null }
}

private val CHOICE_FIELD_PATTERN: Pattern = Pattern.compile("\"choice\"\\s*:\\s*(-?\\d{1,4})")

/**
 * Reads the one integer the `/choose-explanation` endpoint returns.
 *
 * Deliberately narrow: at most four digits, optionally negative, and nothing else is
 * read out of the body at all. An out-of-range or nonsensical value is not this
 * function's problem — [com.steady.app.domain.AdjustmentPhrasings.isValidChoice] bounds
 * it against the list that was offered, and anything that fails falls through to the
 * default sentence.
 */
internal fun extractChoiceField(json: String): Int? {
    val matcher = CHOICE_FIELD_PATTERN.matcher(json)
    if (!matcher.find()) return null
    return matcher.group(1)?.toIntOrNull()
}
