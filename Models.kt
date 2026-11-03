package com.livescore.floating

data class MatchEvent(
    val minute: Int,
    val type: String,
    val detail: String,
    val team: String,
    val player: String
) {
    val isGoal: Boolean get() = type.equals("Goal", true)
    val isCard: Boolean get() = type.equals("Card", true)
    val icon: String
        get() = when {
            isGoal -> "⚽"
            isCard && detail.contains("Red", true) -> "🟥"
            isCard -> "🟨"
            type.equals("subst", true) -> "🔄"
            else -> "•"
        }
}

data class Match(
    val id: Int,
    val league: String,
    val home: String,
    val away: String,
    val homeScore: Int,
    val awayScore: Int,
    val minute: Int?,
    val status: String,
    val events: List<MatchEvent>
) {
    val isLive: Boolean get() = status in setOf("1H", "2H", "ET", "P", "LIVE")
    val clock: String
        get() = when (status) {
            "HT" -> "HT"
            "FT" -> "FT"
            "BT" -> "BT"
            else -> if (minute != null) "$minute'" else status
        }
}
