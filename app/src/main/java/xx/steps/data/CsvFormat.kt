package xx.steps.data

import xx.steps.clampGoal
import xx.steps.isoToDate

/**
 * The CSV shape and the merge rule, free of Android so both are covered by JVM tests. Reading and
 * writing the actual file is `CsvIo.kt`'s job.
 *
 * One line per day: `date,steps,goal` with an ISO date, so the file sorts and diffs sensibly and
 * can be edited by hand in any spreadsheet.
 */

const val CSV_HEADER = "date,steps,goal"

/** Result of reading a file: the days understood, and how many lines were not. */
data class CsvParseResult(
    val days: List<DaySteps>,
    val skipped: Int,
)

fun formatCsv(days: List<DaySteps>): String = buildString {
    appendLine(CSV_HEADER)
    days.sortedBy { it.date }.forEach { day ->
        appendLine("${day.date},${day.steps},${day.goal}")
    }
}

/**
 * Reads what it can and counts what it cannot. A hand-edited file is expected to contain mistakes,
 * and one bad line must not cost the user the rest of the import.
 *
 * Duplicate dates within a file collapse to the larger step count, the same rule that merges an
 * import into the database.
 */
fun parseCsv(text: String): CsvParseResult {
    var skipped = 0
    val byDate = LinkedHashMap<String, DaySteps>()

    text.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        // Blank lines and the header are structure, not data: skipping them is not a failure.
        if (line.isEmpty() || line.equals(CSV_HEADER, ignoreCase = true)) return@forEach

        val parsed = parseLine(line)
        if (parsed == null) {
            skipped++
            return@forEach
        }
        val existing = byDate[parsed.date]
        byDate[parsed.date] = if (existing == null || parsed.steps > existing.steps) parsed else existing
    }

    return CsvParseResult(days = byDate.values.toList(), skipped = skipped)
}

private fun parseLine(line: String): DaySteps? {
    val parts = line.split(',')
    if (parts.size < 2) return null

    val date = parts[0].trim()
    // Parsing the date is the check that it is a date: anything else is a malformed line.
    val valid = runCatching { isoToDate(date) }.isSuccess
    if (!valid) return null

    val steps = parts[1].trim().toIntOrNull() ?: return null
    if (steps < 0) return null

    // A file without the goal column is still usable; those days take the goal in force on import.
    val goal = parts.getOrNull(2)?.trim()?.toIntOrNull()

    return DaySteps(date = date, steps = steps, goal = goal?.let(::clampGoal) ?: 0)
}

/**
 * Folds imported days into what is already stored. The larger step count wins: an import is a
 * restore or a merge from another phone, and the fuller record of a day is the truer one.
 *
 * A day already in the database keeps its own goal — it was judged by that goal at the time.
 * A day arriving fresh takes the goal from the file, or [fallbackGoal] if the file had none.
 */
fun mergeDays(existing: List<DaySteps>, imported: List<DaySteps>, fallbackGoal: Int): List<DaySteps> {
    val byDate = existing.associateBy { it.date }

    return imported.mapNotNull { incoming ->
        val current = byDate[incoming.date]
        val goal = current?.goal ?: incoming.goal.takeIf { it > 0 } ?: fallbackGoal

        when {
            current == null -> incoming.copy(goal = goal)
            incoming.steps > current.steps -> current.copy(steps = incoming.steps, goal = goal)
            else -> null // nothing to write: the stored day already holds at least as much
        }
    }
}
