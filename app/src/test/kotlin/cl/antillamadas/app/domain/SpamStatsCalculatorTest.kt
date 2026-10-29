package cl.antillamadas.app.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas automáticas del motor de Estadísticas y Detección Inteligente (V2).
 */
class SpamStatsCalculatorTest {

  private val santiagoZone = ZoneId.of("America/Santiago")
  private val defaultRules = BuiltInChileRules.defaults()
  private val defaultSettings = FilterSettings()

  @Test
  fun `calcula conteos por prefijo top numeros insistentes tiempo ahorrado y hora pico`() {
    val baseTime = ZonedDateTime.of(2026, 10, 28, 11, 30, 0, 0, santiagoZone)
    val t1 = baseTime.toInstant().toEpochMilli()
    val t2 = baseTime.minusHours(1).toInstant().toEpochMilli() // 10:30
    val t3 = baseTime.minusDays(1).withHour(11).toInstant().toEpochMilli() // Ayer 11:30
    val t4 = baseTime.minusDays(2).withHour(16).toInstant().toEpochMilli() // Hace 2 días 16:00

    val history = listOf(
      BlockedCallRecord("1", "+56 600 600 1000", "600 600 1000", "Prefijo 600", BuiltInChileRules.ID_PREFIX_600, t1),
      BlockedCallRecord("2", "6006001000", "600 600 1000", "Prefijo 600", BuiltInChileRules.ID_PREFIX_600, t3),
      BlockedCallRecord("3", "+566006001000", "600 600 1000", "Prefijo 600", BuiltInChileRules.ID_PREFIX_600, t4),
      BlockedCallRecord("4", "+56 809 123 456", "809 123 456", "Prefijo 809", BuiltInChileRules.ID_PREFIX_809, t2),
    )

    val summary = SpamStatsCalculator.compute(
      blockedHistory = history,
      rules = defaultRules,
      settings = defaultSettings,
      period = StatsPeriod.DAYS_7,
      nowMillis = t1,
      zoneId = santiagoZone,
    )

    assertEquals(4, summary.totalBlocked)
    assertEquals("Tres formatos del mismo 600 deben contarse como 1 único número", 2, summary.uniqueNumbersBlocked)
    assertEquals(4 * SpamStatsCalculator.SECONDS_SAVED_PER_CALL, summary.estimatedSecondsSaved)
    assertEquals("3 min", SpamStatsCalculator.formatSavedTime(summary.estimatedSecondsSaved))

    // Hora pico debe ser 11:00 – 12:00 (2 llamadas a las 11:xx vs 1 a las 10:xx y 1 a las 16:xx)
    assertEquals("11:00 – 12:00", summary.peakHourRange)

    // El prefijo 600 lidera con 3 bloqueos, seguido del 809 con 1:
    assertEquals(BuiltInChileRules.ID_PREFIX_600, summary.ruleBreakdown.first().key)
    assertEquals(3, summary.ruleBreakdown.first().count)

    // El número más insistente es 600 600 1000 con 3 intentos:
    assertEquals(3, summary.topInsistentNumbers.first().count)
    assertEquals("600 600 1000", summary.topInsistentNumbers.first().formattedNumber)

    // Gráfico de 7 días suma las 4 llamadas:
    assertEquals(4, summary.dailyLast7Days.sumOf { it.count })
    assertEquals(2, summary.dailyLast7Days.last().count) // Hoy hubo 2
  }

  @Test
  fun `sugiere bloquear prefijo de 5 digitos solo cuando llaman al menos 2 numeros distintos y no esta bloqueado`() {
    val now = ZonedDateTime.of(2026, 10, 28, 12, 0, 0, 0, santiagoZone).toInstant().toEpochMilli()

    // Caso 1: Un repartidor llama 3 veces desde el MISMO número (mismo fingerprint) -> NO debe sugerir bloqueo
    val sameCallerHits = listOf(
      ObservedPrefixHit("98888", "fp_repartidor", now - 1000),
      ObservedPrefixHit("98888", "fp_repartidor", now - 2000),
      ObservedPrefixHit("98888", "fp_repartidor", now - 3000),
    )
    val noFalsePositive = SpamStatsCalculator.compute(
      blockedHistory = emptyList(),
      rules = defaultRules,
      settings = defaultSettings,
      observedPrefixHits = sameCallerHits,
      nowMillis = now,
      zoneId = santiagoZone,
    )
    assertTrue("No debe sugerir bloqueo si es un único número llamando varias veces", noFalsePositive.smartSuggestions.isEmpty())

    // Caso 2: Call center rota los últimos 4 dígitos sobre 93762 (3 fingerprints distintos) -> SÍ sugiere
    val rotatingPoolHits = listOf(
      ObservedPrefixHit("93762", "fp_1", now - 1000),
      ObservedPrefixHit("93762", "fp_2", now - 2000),
      ObservedPrefixHit("93762", "fp_3", now - 3000),
    )
    val withSuggestion = SpamStatsCalculator.compute(
      blockedHistory = emptyList(),
      rules = defaultRules,
      settings = defaultSettings,
      observedPrefixHits = rotatingPoolHits,
      nowMillis = now,
      zoneId = santiagoZone,
    )
    assertEquals(1, withSuggestion.smartSuggestions.size)
    assertEquals("93762", withSuggestion.smartSuggestions.first().prefix5)
    assertEquals("+56 9 3762 ••••", withSuggestion.smartSuggestions.first().displayPrefix)
    assertEquals(3, withSuggestion.smartSuggestions.first().distinctNumbersCount)

    // Caso 3: Una vez que el usuario bloquea 93762, la sugerencia desaparece sola:
    val blockedRule = FilterRule(
      id = "r_93762",
      label = "Call center 93762",
      pattern = "93762",
      matchType = MatchType.PREFIX,
      kind = RuleKind.BLOCK,
    )
    val afterBlocking = SpamStatsCalculator.compute(
      blockedHistory = emptyList(),
      rules = defaultRules + blockedRule,
      settings = defaultSettings,
      observedPrefixHits = rotatingPoolHits,
      nowMillis = now,
      zoneId = santiagoZone,
    )
    assertTrue(afterBlocking.smartSuggestions.isEmpty())
  }
}
