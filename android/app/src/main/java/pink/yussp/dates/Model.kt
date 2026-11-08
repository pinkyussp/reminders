package pink.yussp.dates

import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID

data class Entry(
    val id: String,
    val name: String,
    val type: String,
    val date: String,
    val remind: Int,
    val rel: String
) {
    fun next(today: LocalDate): LocalDate = nextOccurrence(LocalDate.parse(date), today)

    fun daysUntil(today: LocalDate): Long = ChronoUnit.DAYS.between(today, next(today))

    fun yearsAt(today: LocalDate): Int = next(today).year - LocalDate.parse(date).year

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("type", type)
        .put("date", date)
        .put("remind", remind)
        .put("rel", rel)

    companion object {
        fun fromJson(o: JSONObject): Entry = Entry(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", ""),
            type = o.optString("type", "birthday"),
            date = o.optString("date", ""),
            remind = o.optInt("remind", 3),
            rel = o.optString("rel", "")
        )
    }
}

fun nextOccurrence(base: LocalDate, today: LocalDate): LocalDate {
    val thisYear = occurrenceIn(base, today.year)
    return if (thisYear.isBefore(today)) occurrenceIn(base, today.year + 1) else thisYear
}

fun occurrenceIn(base: LocalDate, year: Int): LocalDate {
    val length = YearMonth.of(year, base.monthValue).lengthOfMonth()
    return LocalDate.of(year, base.monthValue, minOf(base.dayOfMonth, length))
}

fun isValidDate(s: String): Boolean = try {
    LocalDate.parse(s)
    true
} catch (e: Exception) {
    false
}
