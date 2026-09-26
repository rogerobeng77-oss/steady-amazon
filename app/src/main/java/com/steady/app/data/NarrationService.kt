package com.steady.app.data

import android.util.Log
import com.steady.app.domain.AdjustmentKind
import com.steady.app.domain.AdjustmentPhrasings

/** Where a piece of text a person or family member reads actually came from.
 *
 * Three values rather than two, because "a model chose which of our sentences to show"
 * and "a model wrote this sentence" are different promises and the interface says which
 * out loud (see `SPEC.md`). [AI_WRITTEN] only ever appears on text a model composed and
 * [SummaryGuard] then accepted. [AI_CHOSEN] appears on text this app wrote, where a
 * model only picked which wording to use. [RULES] is the deterministic default. */
enum class TextSource { AI_WRITTEN, AI_CHOSEN, RULES }

data class NarratedText(val text: String, val source: TextSource)

/**
 * The boundary between this app and anything a model says, in one file.
 *
 * Two calls go through here and they are deliberately not symmetric:
 *
 * - [adjustmentExplanation] runs on the exercise path, in front of a person who is
 *   about to stand up in their own living room. A model may pick which of
 *   [AdjustmentPhrasings]' own sentences to show and nothing else. The narrator returns
 *   an `Int`; there is no string on the wire, so there is no prose to validate, and a
 *   model that returns 97, or -1, or a paragraph, gets the default sentence. This is a
 *   pattern used elsewhere in this batch of apps: ask a model for a reference, then
 *   resolve it locally.
 *
 * - [summary] runs out of band, on an explicit "Send now", and describes a record to a
 *   family member who will not act on it physically. That one is still free text,
 *   because a warm sentence is the point of it, and [SummaryGuard] checks it before it
 *   is shown or persisted.
 *
 * Both end at the same place when anything goes wrong: the deterministic text, which is
 * already correct and already the default path. Neither ever surfaces a model error, a
 * spinner, or the string a guard refused.
 */
class NarrationService(private val narrator: Narrator) {

    suspend fun summary(facts: SummaryFacts, deterministicFallback: () -> String): NarratedText {
        val aiText = runCatching { narrator.narrateSummary(facts) }.getOrNull()
        if (aiText.isNullOrBlank()) return NarratedText(deterministicFallback(), TextSource.RULES)

        val rejection = SummaryGuard.check(aiText, facts)
        if (rejection != null) {
            // The rule that fired and the term that fired it, never the text itself.
            Log.w(TAG, "weekly summary refused by SummaryGuard: $rejection")
            return NarratedText(deterministicFallback(), TextSource.RULES)
        }
        return NarratedText(aiText.trim(), TextSource.AI_WRITTEN)
    }

    /**
     * The sentence under "Start Today's Session". Always one of this app's own, whatever
     * the model does.
     */
    suspend fun adjustmentExplanation(kind: AdjustmentKind, adjustmentDescription: String): NarratedText {
        val options = AdjustmentPhrasings.options(kind)
        val choice = runCatching {
            narrator.chooseAdjustmentPhrasing(
                PhrasingRequest(kind = kind, adjustmentDescription = adjustmentDescription, options = options),
            )
        }.getOrNull()

        return if (AdjustmentPhrasings.isValidChoice(kind, choice)) {
            NarratedText(AdjustmentPhrasings.resolve(kind, choice), TextSource.AI_CHOSEN)
        } else {
            NarratedText(AdjustmentPhrasings.default(kind), TextSource.RULES)
        }
    }

    private companion object {
        const val TAG = "Steady/Narration"
    }
}
