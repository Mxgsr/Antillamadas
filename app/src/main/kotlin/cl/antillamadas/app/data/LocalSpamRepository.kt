package cl.antillamadas.app.data

import android.content.Context
import cl.antillamadas.app.domain.BlockedCallRecord
import cl.antillamadas.app.domain.BuiltInChileRules
import cl.antillamadas.app.domain.CallFilterEngine
import cl.antillamadas.app.domain.FilterRule
import cl.antillamadas.app.domain.FilterSettings
import cl.antillamadas.app.domain.MatchType
import cl.antillamadas.app.domain.ObservedPrefixHit
import cl.antillamadas.app.domain.PhoneNumberNormalizer
import cl.antillamadas.app.domain.RuleKind
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Todo lo que Antillamadas guarda de forma 100% local en la carpeta privada del teléfono.
 * Compatible hacia atrás con archivos v1.0 gracias a los valores por defecto.
 */
@Serializable
data class SpamStoreData(
  val settings: FilterSettings = FilterSettings(),
  val rules: List<FilterRule> = BuiltInChileRules.defaults(),
  val blockedHistory: List<BlockedCallRecord> = emptyList(),
  val observedPrefixHits: List<ObservedPrefixHit> = emptyList(),
  val dismissedSuggestedPrefixes: Set<String> = emptySet(),
)

/**
 * Formato del archivo `.json` al usar "Exportar / Importar reglas".
 * Por privacidad, solo incluye tus reglas y ajustes (nunca el historial ni prefijos observados).
 */
@Serializable
data class RulesBackup(
  val formatVersion: Int = 2,
  val exportedAtMillis: Long,
  val settings: FilterSettings,
  val rules: List<FilterRule>,
)

sealed interface ImportResult {
  data class Success(val totalCustomRules: Int) : ImportResult
  data class Error(val message: String) : ImportResult
}

/**
 * Repositorio de memoria local.
 *
 * En CS50 con C o Python abrías un archivo (`fopen` / `open`) para guardar datos en disco.
 * Aquí hacemos exactamente eso con un archivo JSON privado (`antillamadas_store.json`),
 * manteniendo además una copia en memoria (`StateFlow`) para que:
 *  1. Cuando entre una llamada, la lectura tome 0 milisegundos.
 *  2. Cuando se bloquee una llamada, la pantalla y las estadísticas se actualicen al instante.
 */
class LocalSpamRepository(
  private val storageFile: File,
) {

  private val lock = Any()

  private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private val _state = MutableStateFlow(loadFromDisk())
  val state: StateFlow<SpamStoreData> = _state.asStateFlow()

  /** Lectura instantánea en memoria para el servicio que intercepta llamadas. */
  fun snapshot(): SpamStoreData = _state.value

  fun setRuleEnabled(ruleId: String, enabled: Boolean) {
    updateStore { current ->
      current.copy(
        rules = current.rules.map { rule ->
          if (rule.id == ruleId) rule.copy(enabled = enabled) else rule
        }
      )
    }
  }

  fun addCustomRule(
    label: String,
    pattern: String,
    matchType: MatchType = MatchType.PREFIX,
    kind: RuleKind = RuleKind.BLOCK,
  ): FilterRule? {
    val sanitizedPattern = sanitizeRawPattern(pattern)
    if (!CallFilterEngine.isValidRulePattern(sanitizedPattern)) return null

    val cleanedPattern = CallFilterEngine.cleanPattern(sanitizedPattern)
    val finalLabel = sanitizeLabel(label, fallbackPattern = cleanedPattern)
    var addedRule: FilterRule? = null

    updateStore { current ->
      val currentCustomCount = current.rules.count { !it.isBuiltIn }
      if (currentCustomCount >= MAX_CUSTOM_RULES) {
        current
      } else {
        val newRule = FilterRule(
          id = UUID.randomUUID().toString(),
          label = finalLabel,
          pattern = sanitizedPattern,
          matchType = matchType,
          kind = kind,
          enabled = true,
          isBuiltIn = false,
        )
        addedRule = newRule
        current.copy(rules = current.rules + newRule)
      }
    }
    return addedRule
  }

  /**
   * Modifica una regla personalizada existente conservando su ID, posición y estado activo/inactivo,
   * y guarda el cambio inmediatamente en disco.
   */
  fun updateCustomRule(
    ruleId: String,
    label: String,
    pattern: String,
    matchType: MatchType,
    kind: RuleKind,
  ): Boolean {
    val sanitizedPattern = sanitizeRawPattern(pattern)
    if (!CallFilterEngine.isValidRulePattern(sanitizedPattern)) return false

    val cleanedPattern = CallFilterEngine.cleanPattern(sanitizedPattern)
    val finalLabel = sanitizeLabel(label, fallbackPattern = cleanedPattern)
    var updatedAny = false

    updateStore { current ->
      current.copy(
        rules = current.rules.map { rule ->
          if (!rule.isBuiltIn && rule.id == ruleId) {
            updatedAny = true
            rule.copy(
              label = finalLabel,
              pattern = sanitizedPattern,
              matchType = matchType,
              kind = kind,
            )
          } else {
            rule
          }
        }
      )
    }
    return updatedAny
  }

  fun deleteRule(ruleId: String) {
    updateStore { current ->
      current.copy(
        // Las reglas de fábrica (809 y 600) no se borran; se apagan o encienden con su interruptor.
        rules = current.rules.filterNot { !it.isBuiltIn && it.id == ruleId }
      )
    }
  }

  fun updateSettings(transform: (FilterSettings) -> FilterSettings) {
    updateStore { current ->
      current.copy(settings = transform(current.settings))
    }
  }

  fun recordBlockedCall(
    rawNumber: String?,
    reason: String,
    matchedRuleId: String? = null,
    timestampMillis: Long = System.currentTimeMillis(),
  ) {
    val safeRaw = rawNumber.orEmpty()
      .filterNot { it.isISOControl() }
      .trim()
      .take(MAX_RAW_NUMBER_LENGTH)
    val safeReason = sanitizeLabel(reason, fallbackPattern = "Spam")

    val record = BlockedCallRecord(
      id = UUID.randomUUID().toString(),
      rawNumber = safeRaw,
      formattedNumber = PhoneNumberNormalizer.formatForDisplay(safeRaw),
      reason = safeReason,
      matchedRuleId = matchedRuleId,
      timestampMillis = timestampMillis,
    )

    updateStore { current ->
      // Insertamos la llamada más reciente al inicio y guardamos hasta MAX_HISTORY_ITEMS.
      val updatedHistory = (listOf(record) + current.blockedHistory).take(MAX_HISTORY_ITEMS)
      current.copy(blockedHistory = updatedHistory)
    }
  }

  /**
   * Registra de forma anónima el prefijo de 5 dígitos de una llamada desconocida (no contacto)
   * que fue permitida, para sugerir bloqueo cuando un call center rota los últimos dígitos.
   * Por privacidad jamás guarda el número completo: usa un hash SHA-256 truncado.
   */
  fun recordObservedUnknownCall(
    rawNumber: String?,
    timestampMillis: Long = System.currentTimeMillis(),
  ) {
    val phone = PhoneNumberNormalizer.normalize(rawNumber)
    if (phone.isHidden || !phone.isChilean) return

    val nat = phone.nationalDigits ?: return
    // Consideramos numeración móvil o fija chilena de 9 dígitos (excluyendo 600 y 809 que ya tienen switch propio).
    if (nat.length != 9 || nat.startsWith("809") || nat.startsWith("600")) return

    val prefix5 = nat.take(5)
    if (!prefix5.all { it.isDigit() }) return

    val canonical = PhoneNumberNormalizer.canonicalKey(rawNumber)
    val fingerprint = shortSha256("antillamadas-v2:$canonical")
    val hit = ObservedPrefixHit(
      prefix5 = prefix5,
      callerFingerprint = fingerprint,
      timestampMillis = timestampMillis,
    )

    updateStore { current ->
      val updatedHits = (listOf(hit) + current.observedPrefixHits).take(MAX_OBSERVED_HITS)
      current.copy(observedPrefixHits = updatedHits)
    }
  }

  fun dismissPrefixSuggestion(prefix5: String) {
    val clean = prefix5.trim()
    if (clean.length != 5 || !clean.all { it.isDigit() }) return

    updateStore { current ->
      val updatedDismissed = (current.dismissedSuggestedPrefixes + clean).take(MAX_DISMISSED_PREFIXES).toSet()
      current.copy(dismissedSuggestedPrefixes = updatedDismissed)
    }
  }

  fun deleteHistoryItem(recordId: String) {
    updateStore { current ->
      current.copy(
        blockedHistory = current.blockedHistory.filterNot { it.id == recordId }
      )
    }
  }

  fun clearHistory() {
    updateStore { current ->
      current.copy(blockedHistory = emptyList())
    }
  }

  /**
   * Genera el texto JSON listo para guardar o compartir como respaldo de tus reglas.
   */
  fun exportRulesJson(nowMillis: Long = System.currentTimeMillis()): String {
    val current = _state.value
    val backup = RulesBackup(
      formatVersion = 2,
      exportedAtMillis = nowMillis,
      settings = current.settings,
      rules = current.rules,
    )
    return json.encodeToString(backup)
  }

  /**
   * Carga reglas desde un archivo JSON exportado previamente.
   * Valida tamaño máximo, sanitiza textos, descarta comodines puros peligrosos ("*") y garantiza IDs únicos.
   */
  fun importRulesJson(
    jsonText: String,
    replaceCustomRules: Boolean = false,
  ): ImportResult {
    if (jsonText.toByteArray(Charsets.UTF_8).size > MAX_IMPORT_BYTES) {
      return ImportResult.Error("El archivo supera el tamaño máximo permitido (256 KB).")
    }

    val parsed = runCatching {
      json.decodeFromString<RulesBackup>(jsonText)
    }.getOrElse {
      return ImportResult.Error("El archivo seleccionado no tiene un formato válido de Antillamadas.")
    }

    var totalCustomCount = 0

    updateStore { current ->
      val importedById = parsed.rules.associateBy { it.id }

      // 1. Mantenemos las reglas oficiales de Chile (809 y 600), respetando si venían activas o no.
      val mergedBuiltIns = BuiltInChileRules.defaults().map { builtIn ->
        val importedMatch = importedById[builtIn.id]
        if (importedMatch != null) {
          builtIn.copy(enabled = importedMatch.enabled)
        } else {
          val existingMatch = current.rules.find { it.id == builtIn.id }
          builtIn.copy(enabled = existingMatch?.enabled ?: builtIn.enabled)
        }
      }

      val usedIds = mergedBuiltIns.mapTo(mutableSetOf()) { it.id }

      // 2. Filtramos y sanitizamos reglas personalizadas válidas del archivo importado.
      val importedCustom = parsed.rules
        .asSequence()
        .filter { !it.isBuiltIn }
        .mapNotNull { sanitizeImportedRule(it) }
        .toList()

      val baseCustom = if (replaceCustomRules) {
        emptyList()
      } else {
        current.rules.filter { !it.isBuiltIn }.onEach { usedIds.add(it.id) }
      }

      // Evitamos duplicar reglas con el mismo tipo/modo/patrón y garantizamos IDs únicos y tope máximo.
      val combinedCustom = buildList {
        addAll(baseCustom)
        for (candidate in importedCustom) {
          if (size >= MAX_CUSTOM_RULES) break
          val candidateKey = ruleDedupKey(candidate)
          val alreadyExists = any { existing -> ruleDedupKey(existing) == candidateKey }
          if (!alreadyExists) {
            val safeId = if (candidate.id.isNotBlank() && usedIds.add(candidate.id)) {
              candidate.id
            } else {
              UUID.randomUUID().toString().also { usedIds.add(it) }
            }
            add(candidate.copy(id = safeId, isBuiltIn = false))
          }
        }
      }

      totalCustomCount = combinedCustom.size

      current.copy(
        settings = parsed.settings,
        rules = mergedBuiltIns + combinedCustom,
      )
    }

    return ImportResult.Success(totalCustomRules = totalCustomCount)
  }

  private fun sanitizeImportedRule(rule: FilterRule): FilterRule? {
    val sanitizedPattern = sanitizeRawPattern(rule.pattern)
    if (!CallFilterEngine.isValidRulePattern(sanitizedPattern)) return null
    val cleanedPattern = CallFilterEngine.cleanPattern(sanitizedPattern)
    val sanitizedLabel = sanitizeLabel(rule.label, fallbackPattern = cleanedPattern)
    val sanitizedId = rule.id.filterNot { it.isISOControl() }.trim().take(64)
    return rule.copy(
      id = sanitizedId,
      label = sanitizedLabel,
      pattern = sanitizedPattern,
      isBuiltIn = false,
    )
  }

  private fun sanitizeRawPattern(rawPattern: String): String {
    return rawPattern
      .filterNot { it.isISOControl() }
      .trim()
      .take(MAX_RAW_PATTERN_LENGTH)
  }

  private fun sanitizeLabel(rawLabel: String, fallbackPattern: String): String {
    val cleaned = rawLabel
      .map { if (it.isISOControl()) ' ' else it }
      .joinToString("")
      .trim()
      .replace(WHITESPACE_REGEX, " ")
      .take(MAX_LABEL_LENGTH)
    return cleaned.ifEmpty { "Patrón $fallbackPattern".take(MAX_LABEL_LENGTH) }
  }

  private fun ruleDedupKey(rule: FilterRule): String {
    return "${rule.kind}:${rule.matchType}:${CallFilterEngine.cleanPattern(rule.pattern)}"
  }

  private fun shortSha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return buildString(12) {
      for (i in 0 until 6) {
        append(String.format("%02x", digest[i]))
      }
    }
  }

  private fun updateStore(transform: (SpamStoreData) -> SpamStoreData) {
    synchronized(lock) {
      val updated = transform(_state.value)
      saveToDisk(updated)
      _state.value = updated
    }
  }

  private fun loadFromDisk(): SpamStoreData {
    synchronized(lock) {
      if (!storageFile.exists()) {
        return SpamStoreData()
      }
      val loaded = runCatching {
        val content = storageFile.readText(Charsets.UTF_8)
        json.decodeFromString<SpamStoreData>(content)
      }.getOrElse {
        SpamStoreData()
      }
      return ensureBuiltInRulesPresent(loaded)
    }
  }

  private fun ensureBuiltInRulesPresent(data: SpamStoreData): SpamStoreData {
    val existingById = data.rules.associateBy { it.id }
    val builtIns = BuiltInChileRules.defaults().map { defaultRule ->
      val saved = existingById[defaultRule.id]
      if (saved != null) defaultRule.copy(enabled = saved.enabled) else defaultRule
    }
    val usedIds = builtIns.mapTo(mutableSetOf()) { it.id }
    val customRules = data.rules
      .asSequence()
      .filter { !it.isBuiltIn && it.id !in usedIds }
      .mapNotNull { sanitizeImportedRule(it) }
      .take(MAX_CUSTOM_RULES)
      .map { rule ->
        val safeId = if (rule.id.isNotBlank() && usedIds.add(rule.id)) {
          rule.id
        } else {
          UUID.randomUUID().toString().also { usedIds.add(it) }
        }
        rule.copy(id = safeId)
      }
      .toList()
    val safeObserved = data.observedPrefixHits
      .filter { it.prefix5.length == 5 && it.prefix5.all(Char::isDigit) && it.callerFingerprint.length <= 24 }
      .take(MAX_OBSERVED_HITS)
    val safeDismissed = data.dismissedSuggestedPrefixes
      .filter { it.length == 5 && it.all(Char::isDigit) }
      .take(MAX_DISMISSED_PREFIXES)
      .toSet()

    return data.copy(
      rules = builtIns + customRules,
      blockedHistory = data.blockedHistory.take(MAX_HISTORY_ITEMS),
      observedPrefixHits = safeObserved,
      dismissedSuggestedPrefixes = safeDismissed,
    )
  }

  /**
   * Escritura atómica: primero escribimos en un archivo temporal `.tmp` y luego lo renombramos.
   * Así, si el teléfono se apaga justo a mitad de guardado, nunca se corrompe tu archivo principal.
   */
  private fun saveToDisk(data: SpamStoreData) {
    storageFile.parentFile?.mkdirs()
    val tempFile = File(storageFile.parentFile, "${storageFile.name}.tmp")
    tempFile.writeText(json.encodeToString(data), Charsets.UTF_8)
    runCatching {
      Files.move(
        tempFile.toPath(),
        storageFile.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE,
      )
    }.onFailure {
      Files.move(
        tempFile.toPath(),
        storageFile.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
      )
    }
  }

  companion object {
    const val MAX_HISTORY_ITEMS = 1000
    const val MAX_OBSERVED_HITS = 400
    const val MAX_DISMISSED_PREFIXES = 200
    const val MAX_CUSTOM_RULES = 500
    const val MAX_IMPORT_BYTES = 256 * 1024
    const val MAX_LABEL_LENGTH = 80
    const val MAX_RAW_PATTERN_LENGTH = 40
    private const val MAX_RAW_NUMBER_LENGTH = 64
    private const val STORE_FILE_NAME = "antillamadas_store.json"
    private val WHITESPACE_REGEX = Regex("\\s+")

    @Volatile
    private var instance: LocalSpamRepository? = null

    /**
     * Devuelve una única instancia compartida en toda la app (patrón Singleton),
     * para que las pantallas y el servicio de llamadas trabajen siempre sobre los mismos datos.
     */
    fun getInstance(context: Context): LocalSpamRepository {
      return instance ?: synchronized(this) {
        instance ?: LocalSpamRepository(
          storageFile = File(context.applicationContext.filesDir, STORE_FILE_NAME)
        ).also { instance = it }
      }
    }
  }
}
