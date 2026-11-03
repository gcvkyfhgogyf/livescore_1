package com.livescore.floating

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single shared source of truth. Polling runs only while the app screen
 * or the floating widget is alive (reference counted) to save battery.
 */
object MatchStore {
    private val _matches = MutableStateFlow<List<Match>>(emptyList())
    val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    private val _pinnedId = MutableStateFlow(-1)
    val pinnedId: StateFlow<Int> = _pinnedId.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var app: Context? = null
    private var consumers = 0
    private var job: Job? = null

    @Synchronized
    fun start(context: Context) {
        if (app == null) {
            app = context.applicationContext
            _pinnedId.value = Prefs(context).pinnedId
        }
        consumers++
        if (job == null) {
            job = scope.launch {
                while (isActive) {
                    refreshInternal()
                    val p = Prefs(app!!)
                    val sec = if (p.apiKey.isBlank()) 5 else p.pollSec
                    delay(sec * 1000L)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        consumers = (consumers - 1).coerceAtLeast(0)
        if (consumers == 0) {
            job?.cancel()
            job = null
        }
    }

    fun refresh() {
        scope.launch { refreshInternal() }
    }

    private suspend fun refreshInternal() {
        val ctx = app ?: return
        _loading.value = true
        try {
            val prefs = Prefs(ctx)
            val fresh = MatchApi.fetchLive(prefs.apiKey)
            if (prefs.notifyGoals) detectGoals(ctx, _matches.value, fresh)
            _matches.value = fresh
            _error.value = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message ?: "Network error"
        } finally {
            _loading.value = false
        }
    }

    private fun detectGoals(ctx: Context, old: List<Match>, fresh: List<Match>) {
        if (old.isEmpty()) return
        val oldById = old.associateBy { it.id }
        for (m in fresh) {
            val o = oldById[m.id] ?: continue
            if (m.homeScore + m.awayScore > o.homeScore + o.awayScore) Notifier.goal(ctx, m)
        }
    }

    fun setPinned(id: Int) {
        val ctx = app ?: return
        _pinnedId.value = id
        Prefs(ctx).pinnedId = id
    }

    fun togglePin(id: Int) = setPinned(if (_pinnedId.value == id) -1 else id)

    /** Match shown in the floating widget. */
    fun widgetMatch(): Match? {
        val l = _matches.value
        val p = _pinnedId.value
        return l.firstOrNull { it.id == p } ?: l.firstOrNull { it.isLive } ?: l.firstOrNull()
    }

    fun cycle() {
        val l = _matches.value
        if (l.isEmpty()) return
        val cur = widgetMatch()
        val i = l.indexOfFirst { it.id == cur?.id }
        setPinned(l[(i + 1) % l.size].id)
    }
}
