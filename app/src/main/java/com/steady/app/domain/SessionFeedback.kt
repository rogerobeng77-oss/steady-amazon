package com.steady.app.domain

/** What the person says about the session right after finishing it. Three choices,
 * D-pad selectable, no typing. This is the input side of the app's only adaptive
 * behaviour: reported difficulty changes tomorrow's dose, not just a logged data point
 * nobody reads. */
enum class SessionFeedback {
    TOO_MUCH,
    JUST_RIGHT,
    TOO_EASY,
}
