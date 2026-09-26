package com.steady.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The one DataStore delegate for `steady_prefs`, in the one file allowed to declare it.
 *
 * `preferencesDataStore` builds a singleton per *delegate*, not per file, so a second
 * `by preferencesDataStore(name = "steady_prefs")` anywhere else in the same process
 * throws `IllegalStateException: There are multiple DataStores active for the same file`
 * the first time it is read — at runtime, from whichever component touched it second.
 * [SteadyRepository] and the debug demo receiver both need this file, so it lives here
 * and neither of them declares its own.
 */
internal val Context.steadyDataStore: DataStore<Preferences> by preferencesDataStore(name = "steady_prefs")
