package cl.antillamadas.app.domain

/**
 * El "cerebro" de Antillamadas.
 * Recibe un número entrante y decide en milisegundos si permitirlo o rechazarlo.
 * No depende de pantallas ni de Android, por lo que podemos probarlo igual que en CS50.
 */
object CallFilterEngine {

  const val MAX_CLEAN_PATTERN_LENGTH = 32
  const val REPEATED_CALL_WINDOW_MILLIS = 3 * 60 * 1000L

  fun evaluate(
    rawNumber: String?,
    rules: List<FilterRule>,
    settings: FilterSettings = FilterSettings(),
    isSavedContact: Boolean = false,
    recentBlockedHistory: List<BlockedCallRecord> = emptyList(),
    communitySpamList: List<CommunitySpamEntry> = emptyList(),
    nowMillis: Long = System.currentTimeMillis(),
  ): FilterDecision {
    // 0. Si el usuario puso la protección en pausa temporal (desde la app o el botón rápido de Android):
    if (!settings.protectionEnabled) {
      return FilterDecision.Allow(reason = "Protección en pausa temporal")
    }

    // 1. Si el número está guardado en tus Contactos y activaste protegerlos, pasa siempre.
    if (isSavedContact && settings.allowContacts) {
      return FilterDecision.Allow(reason = "Contacto guardado en tu agenda")
    }

    val phone = PhoneNumberNormalizer.normalize(rawNumber)

    // 2. Revisamos primero las excepciones (Lista Blanca) activas.
    //    Ejemplo: bloquear todo el 600, pero permitir el 600 específico de tu banco o clínica.
    val activeAllowRules = rules.filter { it.enabled && it.kind == RuleKind.ALLOW }
    for (allowRule in activeAllowRules) {
      if (matchesRule(phone, allowRule)) {
        return FilterDecision.Allow(reason = "Excepción: ${allowRule.label}")
      }
    }

    // 3. ¿Es un número oculto / privado? (No tiene número visible para verificar 2.º intento)
    if (phone.isHidden) {
      return if (settings.blockHiddenNumbers) {
        FilterDecision.Block(reason = "Número oculto o privado")
      } else {
        FilterDecision.Allow(reason = "Número oculto permitido")
      }
    }

    // 4. ¿Es un número internacional (fuera de +56)?
    if (!phone.isChilean && settings.blockInternationalNumbers) {
      if (isRepeatedUrgentAttempt(phone, matchedRuleId = null, settings, recentBlockedHistory, nowMillis)) {
        return FilterDecision.Allow(reason = "Permitido por 2.º intento seguido (< 3 min)")
      }
      return FilterDecision.Block(reason = "Número internacional (${phone.cleaned})")
    }

    // 5. Revisamos todas las reglas de bloqueo activas (600, 809 y patrones personalizados).
    val activeBlockRules = rules.filter { it.enabled && it.kind == RuleKind.BLOCK }
    for (blockRule in activeBlockRules) {
      if (matchesRule(phone, blockRule)) {
        if (isRepeatedUrgentAttempt(phone, blockRule.id, settings, recentBlockedHistory, nowMillis)) {
          return FilterDecision.Allow(reason = "Permitido por 2.º intento seguido (< 3 min)")
        }
        return FilterDecision.Block(
          reason = blockRule.label,
          matchedRuleId = blockRule.id,
        )
      }
    }

    // 5b. Si está activa la base de datos comunitaria de Chile (SpamChile), revisamos reportes:
    if (settings.communitySyncEnabled && communitySpamList.isNotEmpty()) {
      val callerKey = PhoneNumberNormalizer.canonicalKey(phone.raw)
      val communityMatch = communitySpamList.find { it.canonicalNumber == callerKey }
      if (communityMatch != null) {
        if (isRepeatedUrgentAttempt(phone, matchedRuleId = "community_spam", settings, recentBlockedHistory, nowMillis)) {
          return FilterDecision.Allow(reason = "Permitido por 2.º intento seguido (< 3 min)")
        }
        return FilterDecision.Block(
          reason = "Comunidad SpamChile: ${communityMatch.label}",
          matchedRuleId = "community_spam",
        )
      }
    }

    // 6. Si no cayó en ninguna regla, dejamos sonar el teléfono normalmente.
    return FilterDecision.Allow(reason = "Llamada permitida")
  }

  /**
   * Regla anti-emergencias: si el mismo número exacto ya fue cortado hace menos de 3 minutos
   * y vuelve a insistir, dejamos pasar la llamada (salvo el prefijo 809, que por norma Subtel
   * en Chile es exclusivamente spam comercial no solicitado).
   */
  private fun isRepeatedUrgentAttempt(
    phone: NormalizedPhone,
    matchedRuleId: String?,
    settings: FilterSettings,
    recentBlockedHistory: List<BlockedCallRecord>,
    nowMillis: Long,
  ): Boolean {
    if (!settings.allowRepeatedCalls || phone.isHidden) return false
    if (matchedRuleId == BuiltInChileRules.ID_PREFIX_809) return false

    val callerKey = PhoneNumberNormalizer.canonicalKey(phone.raw)
    if (callerKey.isEmpty()) return false

    return recentBlockedHistory.any { record ->
      val elapsed = nowMillis - record.timestampMillis
      elapsed in 0..REPEATED_CALL_WINDOW_MILLIS &&
        PhoneNumberNormalizer.canonicalKey(record.rawNumber) == callerKey
    }
  }

  /**
   * Verifica que un patrón sea seguro y válido:
   * - No vacío ni excesivamente largo (evita abuso de memoria/CPU).
   * - Debe contener al menos 1 dígito numérico tras limpiar el código de país (+56).
   */
  fun isValidRulePattern(rawPattern: String): Boolean {
    val cleaned = cleanPattern(rawPattern)
    if (cleaned.isEmpty() || cleaned.length > MAX_CLEAN_PATTERN_LENGTH) return false

    val effective = when {
      cleaned.startsWith("+56") -> cleaned.removePrefix("+56")
      cleaned.startsWith("56") && cleaned.count { it.isDigit() } >= 11 -> cleaned.removePrefix("56")
      else -> cleaned
    }
    return effective.isNotEmpty() && effective.any { it.isDigit() }
  }

  /**
   * Comprueba si un número ya normalizado coincide con una regla específica.
   */
  fun matchesRule(phone: NormalizedPhone, rule: FilterRule): Boolean {
    if (phone.isHidden || !isValidRulePattern(rule.pattern)) return false

    val cleanedPattern = cleanPattern(rule.pattern)

    val targetText: String
    val effectivePattern: String

    when {
      cleanedPattern.startsWith("+56") -> {
        if (!phone.isChilean) return false
        targetText = phone.nationalDigits ?: return false
        effectivePattern = cleanedPattern.removePrefix("+56")
      }

      cleanedPattern.startsWith("+") -> {
        if (phone.isChilean) return false
        targetText = phone.cleaned
        effectivePattern = cleanedPattern
      }

      cleanedPattern.startsWith("56") && cleanedPattern.count { it.isDigit() } >= 11 -> {
        if (!phone.isChilean) return false
        targetText = phone.nationalDigits ?: return false
        effectivePattern = cleanedPattern.removePrefix("56")
      }

      else -> {
        if (!phone.isChilean) return false
        targetText = phone.nationalDigits ?: return false
        effectivePattern = cleanedPattern
      }
    }

    if (effectivePattern.isEmpty() || effectivePattern.none { it.isDigit() }) return false

    val effectiveMatchType = if ('*' in effectivePattern || '?' in effectivePattern) {
      MatchType.WILDCARD
    } else {
      rule.matchType
    }

    return when (effectiveMatchType) {
      MatchType.PREFIX -> targetText.startsWith(effectivePattern)
      MatchType.EXACT -> targetText == effectivePattern
      MatchType.WILDCARD -> matchesWildcard(targetText, effectivePattern)
    }
  }

  internal fun cleanPattern(rawPattern: String): String {
    val trimmed = rawPattern.trim()
    val filtered = buildString {
      for (char in trimmed) {
        when {
          char.isDigit() || char == '?' -> append(char)
          char == '*' -> {
            if (lastOrNull() != '*') append('*')
          }
          char == '+' && isEmpty() -> append(char)
        }
      }
    }
    return if (filtered.startsWith("00")) {
      "+" + filtered.removePrefix("00")
    } else {
      filtered
    }
  }

  /**
   * Algoritmo iterativo de dos punteros O(N * M) para comparar comodines sin usar Regex.
   */
  internal fun matchesWildcard(text: String, pattern: String): Boolean {
    var tIndex = 0
    var pIndex = 0
    var starIndex = -1
    var matchAfterStar = 0

    while (tIndex < text.length) {
      when {
        pIndex < pattern.length && (pattern[pIndex] == '?' || pattern[pIndex] == text[tIndex]) -> {
          tIndex++
          pIndex++
        }
        pIndex < pattern.length && pattern[pIndex] == '*' -> {
          starIndex = pIndex
          matchAfterStar = tIndex
          pIndex++
        }
        starIndex != -1 -> {
          pIndex = starIndex + 1
          matchAfterStar++
          tIndex = matchAfterStar
        }
        else -> return false
      }
    }

    while (pIndex < pattern.length && pattern[pIndex] == '*') {
      pIndex++
    }

    return pIndex == pattern.length
  }
}
