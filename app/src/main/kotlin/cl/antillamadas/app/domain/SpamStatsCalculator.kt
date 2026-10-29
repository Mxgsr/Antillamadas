package cl.antillamadas.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class StatsPeriod(val label: String, val days: Long?) {
  DAYS_7("7 días", 7),
  DAYS_30("30 días", 30),
  ALL("Todo", null),
}

data class DailyBlockPoint(
  val dayShortLabel: String,
  val dateLabel: String,
  val count: Int,
  val isToday: Boolean,
)

data class TimeSlotStat(
  val id: String,
  val label: String,
  val hoursLabel: String,
  val count: Int,
  val fraction: Float,
)

data class RuleBreakdownItem(
  val key: String,
  val label: String,
  val count: Int,
  val fraction: Float,
)

data class InsistentNumberItem(
  val cleanedNumber: String,
  val rawNumber: String,
  val formattedNumber: String,
  val count: Int,
  val lastReason: String,
  val lastTimestampMillis: Long,
)

data class PrefixSuggestion(
  val prefix5: String,
  val displayPrefix: String,
  val distinctNumbersCount: Int,
  val totalCallsCount: Int,
)

data class SpamStatsSummary(
  val period: StatsPeriod,
  val totalBlocked: Int,
  val uniqueNumbersBlocked: Int,
  val estimatedSecondsSaved: Int,
  val dailyLast7Days: List<DailyBlockPoint>,
  val timeSlots: List<TimeSlotStat>,
  val peakHourRange: String?,
  val ruleBreakdown: List<RuleBreakdownItem>,
  val topInsistentNumbers: List<InsistentNumberItem>,
  val repeatCountsByCleanedNumber: Map<String, Int>,
  val smartSuggestions: List<PrefixSuggestion>,
)

/**
 * Motor de estadísticas 100% local (equivalente a procesar listas y diccionarios `dict` en Python
 * de CS50 para contar frecuencias, agrupar por clave y ordenar rankings).
 */
object SpamStatsCalculator {

  /** Promedio estimado de interrupción evitada por cada llamada spam cortada sin sonar. */
  const val SECONDS_SAVED_PER_CALL = 45
  const val MIN_DISTINCT_NUMBERS_FOR_SUGGESTION = 2
  private const val SUGGESTION_LOOKBACK_DAYS = 14L
  private const val TOP_NUMBERS_LIMIT = 5

  private val chileLocale = Locale.forLanguageTag("es-CL")
  private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM", chileLocale)

  fun compute(
    blockedHistory: List<BlockedCallRecord>,
    rules: List<FilterRule>,
    settings: FilterSettings,
    observedPrefixHits: List<ObservedPrefixHit> = emptyList(),
    dismissedPrefixes: Set<String> = emptySet(),
    period: StatsPeriod = StatsPeriod.DAYS_30,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): SpamStatsSummary {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()

    // Mapa global de cuántas veces ha insistido cada número en todo el historial (útil en Historial)
    val globalRepeats = mutableMapOf<String, Int>()
    for (record in blockedHistory) {
      val key = PhoneNumberNormalizer.canonicalKey(record.rawNumber)
      if (key.isNotEmpty()) {
        globalRepeats[key] = (globalRepeats[key] ?: 0) + 1
      }
    }

    // 1. Filtramos registros según el período seleccionado (7 días, 30 días o Todo)
    val cutoffMillis = period.days?.let { days ->
      today.minusDays(days - 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
    val filteredRecords = if (cutoffMillis != null) {
      blockedHistory.filter { it.timestampMillis >= cutoffMillis }
    } else {
      blockedHistory
    }

    // 2. Actividad diaria de los últimos 7 días calendario (siempre visible y ordenada Lun..Dom)
    val dailyCountsByDate = mutableMapOf<LocalDate, Int>()
    val sevenDaysStart = today.minusDays(6)
    for (record in blockedHistory) {
      val date = Instant.ofEpochMilli(record.timestampMillis).atZone(zoneId).toLocalDate()
      if (!date.isBefore(sevenDaysStart) && !date.isAfter(today)) {
        dailyCountsByDate[date] = (dailyCountsByDate[date] ?: 0) + 1
      }
    }
    val dailyLast7Days = (6 downTo 0).map { offset ->
      val date = today.minusDays(offset.toLong())
      DailyBlockPoint(
        dayShortLabel = shortDayName(date.dayOfWeek.value),
        dateLabel = date.format(dateFormatter),
        count = dailyCountsByDate[date] ?: 0,
        isToday = date == today,
      )
    }

    // 3. Horarios punta (Mañana, Tarde, Noche) + hora pico exacta
    var morningCount = 0
    var afternoonCount = 0
    var nightCount = 0
    val hourHistogram = IntArray(24)

    for (record in filteredRecords) {
      val hour = Instant.ofEpochMilli(record.timestampMillis).atZone(zoneId).hour
      hourHistogram[hour]++
      when (hour) {
        in 6..13 -> morningCount++
        in 14..18 -> afternoonCount++
        else -> nightCount++
      }
    }

    val total = filteredRecords.size
    val totalFloat = total.coerceAtLeast(1).toFloat()
    val timeSlots = listOf(
      TimeSlotStat("morning", "Mañana", "06:00 – 13:59", morningCount, morningCount / totalFloat),
      TimeSlotStat("afternoon", "Tarde", "14:00 – 18:59", afternoonCount, afternoonCount / totalFloat),
      TimeSlotStat("night", "Noche", "19:00 – 05:59", nightCount, nightCount / totalFloat),
    )

    val peakHourRange = if (total > 0) {
      val bestHour = hourHistogram.indices.maxByOrNull { hourHistogram[it] } ?: 0
      val nextHour = (bestHour + 1) % 24
      String.format(Locale.US, "%02d:00 – %02d:00", bestHour, nextHour)
    } else {
      null
    }

    // 4. Desglose por Prefijo / Regla
    val rulesById = rules.associateBy { it.id }
    val ruleCounts = mutableMapOf<String, Pair<String, Int>>()
    for (record in filteredRecords) {
      val (groupKey, groupLabel) = classifyRuleGroup(record, rulesById)
      val current = ruleCounts[groupKey]
      ruleCounts[groupKey] = groupLabel to ((current?.second ?: 0) + 1)
    }
    val ruleBreakdown = ruleCounts.entries
      .map { (key, pair) ->
        RuleBreakdownItem(
          key = key,
          label = pair.first,
          count = pair.second,
          fraction = pair.second / totalFloat,
        )
      }
      .sortedByDescending { it.count }

    // 5. Ranking de números exactos más insistentes
    data class NumberAccumulator(
      val rawNumber: String,
      val formattedNumber: String,
      var count: Int,
      var lastReason: String,
      var lastTimestampMillis: Long,
    )

    val byNumber = mutableMapOf<String, NumberAccumulator>()
    for (record in filteredRecords) {
      val key = PhoneNumberNormalizer.canonicalKey(record.rawNumber)
      if (key.isEmpty()) continue
      val existing = byNumber[key]
      if (existing == null) {
        byNumber[key] = NumberAccumulator(
          rawNumber = record.rawNumber,
          formattedNumber = record.formattedNumber,
          count = 1,
          lastReason = record.reason,
          lastTimestampMillis = record.timestampMillis,
        )
      } else {
        existing.count++
        if (record.timestampMillis > existing.lastTimestampMillis) {
          existing.lastTimestampMillis = record.timestampMillis
          existing.lastReason = record.reason
        }
      }
    }

    val topInsistent = byNumber.entries
      .sortedWith(
        compareByDescending<Map.Entry<String, NumberAccumulator>> { it.value.count }
          .thenByDescending { it.value.lastTimestampMillis }
      )
      .take(TOP_NUMBERS_LIMIT)
      .map { (cleaned, acc) ->
        InsistentNumberItem(
          cleanedNumber = cleaned,
          rawNumber = acc.rawNumber,
          formattedNumber = acc.formattedNumber,
          count = acc.count,
          lastReason = acc.lastReason,
          lastTimestampMillis = acc.lastTimestampMillis,
        )
      }

    // 6. Sugerencias inteligentes de prefijos repetidos (pools rotativos de call centers)
    val smartSuggestions = computeSmartSuggestions(
      observedPrefixHits = observedPrefixHits,
      rules = rules,
      settings = settings,
      dismissedPrefixes = dismissedPrefixes,
      today = today,
      zoneId = zoneId,
    )

    return SpamStatsSummary(
      period = period,
      totalBlocked = total,
      uniqueNumbersBlocked = byNumber.size,
      estimatedSecondsSaved = total * SECONDS_SAVED_PER_CALL,
      dailyLast7Days = dailyLast7Days,
      timeSlots = timeSlots,
      peakHourRange = peakHourRange,
      ruleBreakdown = ruleBreakdown,
      topInsistentNumbers = topInsistent,
      repeatCountsByCleanedNumber = globalRepeats,
      smartSuggestions = smartSuggestions,
    )
  }

  /**
   * Formatea segundos ahorrados de forma amigable (ej. "45 s", "12 min", "1 h 20 min").
   */
  fun formatSavedTime(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0 min"
    if (totalSeconds < 60) return "$totalSeconds s"
    val totalMinutes = (totalSeconds + 30) / 60
    if (totalMinutes < 60) return "$totalMinutes min"
    val hours = totalMinutes / 60
    val remMinutes = totalMinutes % 60
    return if (remMinutes == 0) "$hours h" else "$hours h $remMinutes min"
  }

  private fun computeSmartSuggestions(
    observedPrefixHits: List<ObservedPrefixHit>,
    rules: List<FilterRule>,
    settings: FilterSettings,
    dismissedPrefixes: Set<String>,
    today: LocalDate,
    zoneId: ZoneId,
  ): List<PrefixSuggestion> {
    if (observedPrefixHits.isEmpty()) return emptyList()

    val cutoffMillis = today
      .minusDays(SUGGESTION_LOOKBACK_DAYS)
      .atStartOfDay(zoneId)
      .toInstant()
      .toEpochMilli()

    val recentHits = observedPrefixHits.filter { hit ->
      hit.timestampMillis >= cutoffMillis &&
        hit.prefix5.length == 5 &&
        hit.prefix5.all { it.isDigit() } &&
        hit.prefix5 !in dismissedPrefixes
    }

    return recentHits
      .groupBy { it.prefix5 }
      .mapNotNull { (prefix5, hits) ->
        val distinctCallers = hits.mapTo(mutableSetOf()) { it.callerFingerprint }.size
        if (distinctCallers < MIN_DISTINCT_NUMBERS_FOR_SUGGESTION) {
          return@mapNotNull null
        }

        // Si el usuario ya tiene una regla activa que bloquea este prefijo, no se lo sugerimos de nuevo.
        val sampleDecision = CallFilterEngine.evaluate(
          rawNumber = "+56${prefix5}0000",
          rules = rules,
          settings = settings,
          isSavedContact = false,
        )
        if (sampleDecision is FilterDecision.Block) {
          return@mapNotNull null
        }

        PrefixSuggestion(
          prefix5 = prefix5,
          displayPrefix = formatPrefix5ForDisplay(prefix5),
          distinctNumbersCount = distinctCallers,
          totalCallsCount = hits.size,
        )
      }
      .sortedWith(
        compareByDescending<PrefixSuggestion> { it.distinctNumbersCount }
          .thenByDescending { it.totalCallsCount }
      )
      .take(3)
  }

  private fun formatPrefix5ForDisplay(prefix5: String): String {
    if (prefix5.length != 5) return "+56 $prefix5"
    val first = prefix5.first()
    val next4 = prefix5.substring(1)
    return if (first == '9' || first == '2') {
      "+56 $first $next4 ••••"
    } else {
      "+56 ${prefix5.substring(0, 2)} ${prefix5.substring(2)}• ••••"
    }
  }

  private fun classifyRuleGroup(
    record: BlockedCallRecord,
    rulesById: Map<String, FilterRule>,
  ): Pair<String, String> {
    val matchedId = record.matchedRuleId
    if (matchedId == BuiltInChileRules.ID_PREFIX_809) {
      return BuiltInChileRules.ID_PREFIX_809 to "Prefijo 809 (Spam comercial)"
    }
    if (matchedId == BuiltInChileRules.ID_PREFIX_600) {
      return BuiltInChileRules.ID_PREFIX_600 to "Prefijo 600 (Masivos / cobranza)"
    }
    if (matchedId != null) {
      val currentRule = rulesById[matchedId]
      val label = currentRule?.label ?: record.reason
      return "rule:$matchedId" to label
    }

    return when {
      record.reason.startsWith("Número oculto", ignoreCase = true) ->
        "hidden" to "Números ocultos / privados"
      record.reason.startsWith("Número internacional", ignoreCase = true) ->
        "international" to "Llamadas internacionales"
      else ->
        "other:${record.reason}" to record.reason
    }
  }

  private fun shortDayName(isoDayOfWeek: Int): String = when (isoDayOfWeek) {
    1 -> "Lun"
    2 -> "Mar"
    3 -> "Mié"
    4 -> "Jue"
    5 -> "Vie"
    6 -> "Sáb"
    else -> "Dom"
  }
}
