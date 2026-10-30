package cl.antillamadas.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Equivalente a `check50` en CS50: cada función `@Test` prueba automáticamente un escenario real
 * para asegurarnos de que nuestro motor de bloqueo nunca falle ni corte llamadas equivocadas.
 */
class CallFilterEngineTest {

  private val defaultRules = BuiltInChileRules.defaults()

  @Test
  fun `bloquea llamadas 809 en todos los formatos chilenos habituales`() {
    val formats = listOf(
      "+56 809 123 456",
      "+56809123456",
      "809123456",
      "809-123-456",
      "56809123456",
      "0056809123456",
    )

    for (number in formats) {
      val decision = CallFilterEngine.evaluate(number, defaultRules)
      assertTrue("Debería bloquear $number", decision is FilterDecision.Block)
      val block = decision as FilterDecision.Block
      assertEquals(BuiltInChileRules.ID_PREFIX_809, block.matchedRuleId)
    }
  }

  @Test
  fun `bloquea llamadas 600 en todos los formatos chilenos habituales`() {
    val formats = listOf(
      "+56 600 600 1000",
      "+566006001000",
      "600 600 1000",
      "6006001000",
      "566006001000",
    )

    for (number in formats) {
      val decision = CallFilterEngine.evaluate(number, defaultRules)
      assertTrue("Debería bloquear $number", decision is FilterDecision.Block)
      val block = decision as FilterDecision.Block
      assertEquals(BuiltInChileRules.ID_PREFIX_600, block.matchedRuleId)
    }
  }

  @Test
  fun `permite celulares chilenos normales cuando no coinciden con spam`() {
    val decision = CallFilterEngine.evaluate("+56 9 8765 4321", defaultRules)
    assertTrue(decision is FilterDecision.Allow)
  }

  @Test
  fun `no confunde un celular espanol +34 600 con el prefijo 600 chileno`() {
    val decision = CallFilterEngine.evaluate("+34 600 11 22 33", defaultRules)
    assertTrue(
      "Un móvil de España (+34 600...) no es un 600 chileno",
      decision is FilterDecision.Allow,
    )
  }

  @Test
  fun `bloquea patrones personalizados repetidos de call centers moviles o fijos`() {
    val customRule = FilterRule(
      id = "custom_1",
      label = "Call center móvil 93762",
      pattern = "+56 9 3762*",
      matchType = MatchType.WILDCARD,
      kind = RuleKind.BLOCK,
    )
    val rules = defaultRules + customRule

    assertTrue(CallFilterEngine.evaluate("+56937620001", rules) is FilterDecision.Block)
    assertTrue(CallFilterEngine.evaluate("9 3762 9988", rules) is FilterDecision.Block)
    assertTrue(CallFilterEngine.evaluate("+56937630001", rules) is FilterDecision.Allow)
  }

  @Test
  fun `respeta excepciones de lista blanca por encima del bloqueo general 600`() {
    val bankException = FilterRule(
      id = "allow_bank",
      label = "Mi Banco",
      pattern = "600 320 3000",
      matchType = MatchType.EXACT,
      kind = RuleKind.ALLOW,
    )
    val rules = defaultRules + bankException

    // El 600 de nuestro banco pasa:
    assertTrue(CallFilterEngine.evaluate("+56 600 320 3000", rules) is FilterDecision.Allow)
    // Cualquier otro 600 sigue bloqueado:
    assertTrue(CallFilterEngine.evaluate("+56 600 999 0000", rules) is FilterDecision.Block)
  }

  @Test
  fun `nunca bloquea contactos guardados cuando la proteccion de agenda esta activa`() {
    val decision = CallFilterEngine.evaluate(
      rawNumber = "+56 600 123 4567",
      rules = defaultRules,
      settings = FilterSettings(allowContacts = true),
      isSavedContact = true,
    )
    assertTrue(decision is FilterDecision.Allow)
  }

  @Test
  fun `bloquea numeros ocultos e internacionales solo cuando el usuario lo activa`() {
    val strictSettings = FilterSettings(
      blockHiddenNumbers = true,
      blockInternationalNumbers = true,
    )

    assertTrue(CallFilterEngine.evaluate("", defaultRules, strictSettings) is FilterDecision.Block)
    assertTrue(CallFilterEngine.evaluate("unknown", defaultRules, strictSettings) is FilterDecision.Block)
    assertTrue(CallFilterEngine.evaluate("+1 800 555 0199", defaultRules, strictSettings) is FilterDecision.Block)
  }

  @Test
  fun `ignora comodines puros sin digitos y evalua comodines complejos en tiempo lineal`() {
    assertFalse(CallFilterEngine.isValidRulePattern("*"))
    assertFalse(CallFilterEngine.isValidRulePattern("???"))
    assertFalse(CallFilterEngine.isValidRulePattern("+56*"))
    assertTrue(CallFilterEngine.isValidRulePattern("+56 9*"))

    val dangerousAllowAll = FilterRule(
      id = "bad_allow",
      label = "Trampa permitir todo",
      pattern = "*",
      matchType = MatchType.WILDCARD,
      kind = RuleKind.ALLOW,
    )
    // Una regla maliciosa "*" en Lista Blanca NO debe desactivar el bloqueo del 809:
    val decision = CallFilterEngine.evaluate("+56 809 123 456", defaultRules + dangerousAllowAll)
    assertTrue(decision is FilterDecision.Block)

    // Comodines múltiples con '*' y '?' sin usar Regex:
    assertTrue(CallFilterEngine.matchesWildcard("937620099", "937***62??99"))
    assertFalse(CallFilterEngine.matchesWildcard("9999999999999999", "*9*9*9*9*8"))
  }

  @Test
  fun `deja pasar todas las llamadas cuando la proteccion esta en pausa temporal`() {
    val pausedSettings = FilterSettings(protectionEnabled = false)
    val decision = CallFilterEngine.evaluate(
      rawNumber = "+56 809 123 456",
      rules = defaultRules,
      settings = pausedSettings,
    )
    assertTrue(decision is FilterDecision.Allow)
  }

  @Test
  fun `deja pasar al segundo intento seguido en menos de 3 min excepto para el prefijo 809`() {
    val customCallCenter = FilterRule(
      id = "custom_93762",
      label = "Call center 93762",
      pattern = "93762",
      matchType = MatchType.PREFIX,
      kind = RuleKind.BLOCK,
    )
    val rules = defaultRules + customCallCenter
    val settings = FilterSettings(allowRepeatedCalls = true)
    val t0 = 1_700_000_000_000L

    // 1. Primer intento de un 600 o móvil: se bloquea normalmente
    val firstAttempt = CallFilterEngine.evaluate(
      rawNumber = "6006001000",
      rules = rules,
      settings = settings,
      recentBlockedHistory = emptyList(),
      nowMillis = t0,
    )
    assertTrue(firstAttempt is FilterDecision.Block)

    val historyAfterFirst = listOf(
      BlockedCallRecord(
        id = "rec_1",
        rawNumber = "6006001000",
        formattedNumber = "600 600 1000",
        reason = "Prefijo 600",
        matchedRuleId = BuiltInChileRules.ID_PREFIX_600,
        timestampMillis = t0,
      )
    )

    // 2. Segundo intento del mismo número 90 segundos después (incluso con +56 y espacios): pasa por urgencia
    val secondWithinWindow = CallFilterEngine.evaluate(
      rawNumber = "+56 600 600 1000",
      rules = rules,
      settings = settings,
      recentBlockedHistory = historyAfterFirst,
      nowMillis = t0 + 90_000L,
    )
    assertTrue(secondWithinWindow is FilterDecision.Allow)

    // 3. Si llama recién a los 5 minutos (> 3 min), se vuelve a bloquear:
    val afterWindowExpired = CallFilterEngine.evaluate(
      rawNumber = "+56 600 600 1000",
      rules = rules,
      settings = settings,
      recentBlockedHistory = historyAfterFirst,
      nowMillis = t0 + 5 * 60_000L,
    )
    assertTrue(afterWindowExpired is FilterDecision.Block)

    // 4. Regla Subtel Chile: un 809 JAMÁS pasa ni siquiera al 2.º intento seguido
    val history809 = listOf(
      BlockedCallRecord(
        id = "rec_809",
        rawNumber = "+56809123456",
        formattedNumber = "809 123 456",
        reason = "Prefijo 809",
        matchedRuleId = BuiltInChileRules.ID_PREFIX_809,
        timestampMillis = t0,
      )
    )
    val second809 = CallFilterEngine.evaluate(
      rawNumber = "809 123 456",
      rules = rules,
      settings = settings,
      recentBlockedHistory = history809,
      nowMillis = t0 + 30_000L,
    )
    assertTrue("El prefijo 809 es spam comercial puro y nunca debe pasar al 2.º intento", second809 is FilterDecision.Block)
  }

  @Test
  fun `formatea numeros limpios sin espacios ni +56 para copiar a No Molestar SERNAC`() {
    assertEquals("6006001000", PhoneNumberNormalizer.formatForSernac("+56 600 600 1000"))
    assertEquals("809123456", PhoneNumberNormalizer.formatForSernac("809-123-456"))
    assertEquals("937628491", PhoneNumberNormalizer.formatForSernac("+56 9 3762 8491"))
    assertEquals("", PhoneNumberNormalizer.formatForSernac("unknown"))
  }

  @Test
  fun `bloquea numeros de la base comunitaria solo cuando la opcion esta activa`() {
    val communityList = listOf(
      CommunitySpamEntry(
        canonicalNumber = "+56442760172",
        rawNumber = "+56442760172",
        label = "Fraude SMS",
      ),
      CommunitySpamEntry(
        canonicalNumber = "+56965130021",
        rawNumber = "56965130021",
        label = "Robocaller",
      ),
    )

    // 1. Con la opción desactivada (valor por defecto), el número móvil no se bloquea por comunidad:
    val disabledSettings = FilterSettings(communitySyncEnabled = false)
    val decisionDisabled = CallFilterEngine.evaluate(
      rawNumber = "+56 9 6513 0021",
      rules = defaultRules,
      settings = disabledSettings,
      communitySpamList = communityList,
    )
    assertTrue(decisionDisabled is FilterDecision.Allow)

    // 2. Con la opción activada, se bloquea con la etiqueta comunitaria:
    val enabledSettings = FilterSettings(communitySyncEnabled = true)
    val decisionEnabled = CallFilterEngine.evaluate(
      rawNumber = "9 6513 0021",
      rules = defaultRules,
      settings = enabledSettings,
      communitySpamList = communityList,
    )
    assertTrue(decisionEnabled is FilterDecision.Block)
    val block = decisionEnabled as FilterDecision.Block
    assertEquals("community_spam", block.matchedRuleId)
    assertTrue(block.reason.contains("Robocaller"))

    // 3. Excepción de lista blanca del usuario SIEMPRE tiene prioridad sobre la comunidad:
    val allowException = FilterRule(
      id = "allow_robocaller",
      label = "Excepción autorizada",
      pattern = "965130021",
      matchType = MatchType.EXACT,
      kind = RuleKind.ALLOW,
    )
    val decisionWithAllow = CallFilterEngine.evaluate(
      rawNumber = "+56 9 6513 0021",
      rules = defaultRules + allowException,
      settings = enabledSettings,
      communitySpamList = communityList,
    )
    assertTrue("La lista blanca del usuario debe tener prioridad", decisionWithAllow is FilterDecision.Allow)
  }
}
