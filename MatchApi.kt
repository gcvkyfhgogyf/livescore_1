package com.livescore.floating

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Uses API-Football (api-sports.io). Blank key -> built-in demo data. */
object MatchApi {

    suspend fun fetchLive(apiKey: String): List<Match> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext DemoData.matches()

        val conn = URL("https://v3.football.api-sports.io/fixtures?live=all")
            .openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("x-apisports-key", apiKey)
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode != 200) error("Server error ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): List<Match> {
        val root = JSONObject(body)
        val errs = root.optJSONObject("errors")
        if (errs != null && errs.length() > 0) {
            error(errs.optString(errs.keys().next(), "API error"))
        }
        val arr = root.optJSONArray("response") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val fx = o.getJSONObject("fixture")
            val st = fx.getJSONObject("status")
            val teams = o.getJSONObject("teams")
            val goals = o.getJSONObject("goals")
            val evArr = o.optJSONArray("events")
            val events = if (evArr == null) emptyList() else (0 until evArr.length()).map { j ->
                val e = evArr.getJSONObject(j)
                MatchEvent(
                    minute = e.optJSONObject("time")?.optInt("elapsed") ?: 0,
                    type = e.optString("type"),
                    detail = e.optString("detail"),
                    team = e.optJSONObject("team")?.optString("name") ?: "",
                    player = e.optJSONObject("player")?.optString("name") ?: ""
                )
            }
            Match(
                id = fx.getInt("id"),
                league = o.getJSONObject("league").optString("name"),
                home = teams.getJSONObject("home").optString("name"),
                away = teams.getJSONObject("away").optString("name"),
                homeScore = goals.optInt("home", 0),
                awayScore = goals.optInt("away", 0),
                minute = if (st.isNull("elapsed")) null else st.getInt("elapsed"),
                status = st.optString("short"),
                events = events
            )
        }
    }
}

/** Stateless simulated matches so the app and widget can be tried without an API key. */
object DemoData {
    private class Spec(
        val id: Int, val league: String, val home: String, val away: String,
        val homeGoals: List<Int>, val awayGoals: List<Int>, val offset: Int
    )

    private val specs = listOf(
        Spec(1, "Premier League", "Arsenal", "Chelsea", listOf(12, 55), listOf(38), 0),
        Spec(2, "La Liga", "Barcelona", "Real Madrid", listOf(23, 71, 80), listOf(44, 66), 20),
        Spec(3, "Serie A", "Inter", "Juventus", emptyList(), listOf(61), 45),
        Spec(4, "Bundesliga", "Bayern", "Dortmund", listOf(8, 33, 59), emptyList(), 70)
    )
    private val players = listOf("Silva", "Rodri", "Haaland", "Saka", "Lewa", "Vini", "Kane", "Lautaro")

    fun matches(): List<Match> {
        val tick = System.currentTimeMillis() / 3000 // 3 real seconds = 1 match minute
        return specs.map { s ->
            val t = ((tick + s.offset) % 110).toInt()
            val (status, m) = when {
                t < 45 -> "1H" to t + 1
                t < 50 -> "HT" to 45
                t < 95 -> "2H" to t - 50 + 46
                else -> "FT" to 90
            }
            val events = mutableListOf<MatchEvent>()
            s.homeGoals.filter { it <= m }.forEach {
                events += MatchEvent(it, "Goal", "Normal Goal", s.home, players[(it + s.id) % players.size])
            }
            s.awayGoals.filter { it <= m }.forEach {
                events += MatchEvent(it, "Goal", "Normal Goal", s.away, players[(it + s.id + 3) % players.size])
            }
            if (m >= 30) events += MatchEvent(30, "Card", "Yellow Card", s.away, players[s.id])
            events.sortBy { it.minute }
            Match(
                id = s.id, league = s.league, home = s.home, away = s.away,
                homeScore = s.homeGoals.count { it <= m },
                awayScore = s.awayGoals.count { it <= m },
                minute = m, status = status, events = events
            )
        }
    }
}
