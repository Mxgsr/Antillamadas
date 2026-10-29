package cl.antillamadas.app.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cl.antillamadas.app.data.ImportResult
import cl.antillamadas.app.data.LocalSpamRepository
import cl.antillamadas.app.data.SpamStoreData
import cl.antillamadas.app.domain.CallFilterEngine
import cl.antillamadas.app.domain.FilterDecision
import cl.antillamadas.app.domain.MatchType
import cl.antillamadas.app.domain.PhoneNumberNormalizer
import cl.antillamadas.app.domain.RuleKind
import kotlinx.coroutines.flow.StateFlow

/**
 * El ViewModel actúa como puente entre los datos guardados (`LocalSpamRepository`)
 * y las pantallas de Jetpack Compose, manteniendo la pantalla sincronizada aunque gires el celular.
 */
class AntillamadasViewModel(
  private val repository: LocalSpamRepository,
) : ViewModel() {

  val storeState: StateFlow<SpamStoreData> = repository.state

  fun toggleRule(ruleId: String, enabled: Boolean) {
    repository.setRuleEnabled(ruleId, enabled)
  }

  fun addRule(
    label: String,
    pattern: String,
    matchType: MatchType,
    kind: RuleKind,
  ): Boolean {
    return repository.addCustomRule(
      label = label,
      pattern = pattern,
      matchType = matchType,
      kind = kind,
    ) != null
  }

  fun updateRule(
    ruleId: String,
    label: String,
    pattern: String,
    matchType: MatchType,
    kind: RuleKind,
  ): Boolean {
    return repository.updateCustomRule(
      ruleId = ruleId,
      label = label,
      pattern = pattern,
      matchType = matchType,
      kind = kind,
    )
  }

  fun deleteRule(ruleId: String) {
    repository.deleteRule(ruleId)
  }

  fun setProtectionEnabled(enabled: Boolean) {
    repository.updateSettings { it.copy(protectionEnabled = enabled) }
  }

  fun setAllowContacts(enabled: Boolean) {
    repository.updateSettings { it.copy(allowContacts = enabled) }
  }

  fun setAllowRepeatedCalls(enabled: Boolean) {
    repository.updateSettings { it.copy(allowRepeatedCalls = enabled) }
  }

  fun setBlockHiddenNumbers(enabled: Boolean) {
    repository.updateSettings { it.copy(blockHiddenNumbers = enabled) }
  }

  fun setBlockInternationalNumbers(enabled: Boolean) {
    repository.updateSettings { it.copy(blockInternationalNumbers = enabled) }
  }

  /**
   * Bloquea en 1 toque un prefijo repetido sugerido en la pestaña Estadísticas.
   */
  fun blockSuggestedPrefix(prefix5: String): Boolean {
    val clean = prefix5.trim()
    if (clean.length != 5 || !clean.all { it.isDigit() }) return false
    val readable = "+56 ${clean.first()} ${clean.substring(1)}"
    return repository.addCustomRule(
      label = "Call center $readable",
      pattern = clean,
      matchType = MatchType.PREFIX,
      kind = RuleKind.BLOCK,
    ) != null
  }

  fun dismissPrefixSuggestion(prefix5: String) {
    repository.dismissPrefixSuggestion(prefix5)
  }

  /**
   * Crea una excepción exacta desde el Historial por si alguna vez quieres dejar pasar
   * un número específico (por ejemplo, el 600 puntual de tu banco o clínica).
   */
  fun allowNumberFromHistory(rawNumber: String): Boolean {
    val phone = PhoneNumberNormalizer.normalize(rawNumber)
    if (phone.isHidden) return false

    val patternToAllow = if (phone.isChilean) {
      phone.nationalDigits ?: return false
    } else {
      phone.cleaned
    }

    val display = PhoneNumberNormalizer.formatForDisplay(rawNumber)
    return repository.addCustomRule(
      label = "Permitido: $display",
      pattern = patternToAllow,
      matchType = MatchType.EXACT,
      kind = RuleKind.ALLOW,
    ) != null
  }

  fun deleteHistoryItem(recordId: String) {
    repository.deleteHistoryItem(recordId)
  }

  fun clearHistory() {
    repository.clearHistory()
  }

  fun exportRulesJson(): String = repository.exportRulesJson()

  fun importRulesJson(jsonText: String): ImportResult = repository.importRulesJson(jsonText)

  fun simulateCall(rawNumber: String): FilterDecision {
    val current = repository.snapshot()
    return CallFilterEngine.evaluate(
      rawNumber = rawNumber,
      rules = current.rules,
      settings = current.settings,
      isSavedContact = false,
      recentBlockedHistory = current.blockedHistory,
    )
  }

  companion object {
    fun factory(context: Context): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return AntillamadasViewModel(
            repository = LocalSpamRepository.getInstance(context.applicationContext),
          ) as T
        }
      }
  }
}
