package cl.antillamadas.app.domain

import kotlinx.serialization.Serializable

/**
 * En CS50 usabas `enum` o constantes en C para representar opciones fijas.
 * Aquí definimos si una regla sirve para BLOQUEAR o para PERMITIR (excepción / lista blanca).
 *
 * `@Serializable` le enseña a Kotlin a convertir este dato a texto JSON automáticamente
 * para guardarlo en la memoria interna del teléfono o exportarlo a un archivo.
 */
@Serializable
enum class RuleKind {
  BLOCK,
  ALLOW,
}

/**
 * Cómo se compara el patrón escrito por el usuario contra el número que llama:
 * - PREFIX: el número comienza con esos dígitos (ej. "600", "809", "93762").
 * - WILDCARD: usa comodines estilo terminal ('*' = cualquier secuencia, '?' = un dígito).
 * - EXACT: debe coincidir el número completo (útil para bloquear o permitir un número puntual).
 */
@Serializable
enum class MatchType {
  PREFIX,
  WILDCARD,
  EXACT,
}

/**
 * Una `data class` en Kotlin es equivalente a un `struct` de C en CS50,
 * pero incluye automáticamente comparación, copia y formato legible.
 */
@Serializable
data class FilterRule(
  val id: String,
  val label: String,
  val pattern: String,
  val matchType: MatchType = MatchType.PREFIX,
  val kind: RuleKind = RuleKind.BLOCK,
  val enabled: Boolean = true,
  val isBuiltIn: Boolean = false,
)

/**
 * Ajustes generales del filtro que acompañan a la lista de reglas.
 */
@Serializable
data class FilterSettings(
  val protectionEnabled: Boolean = true,
  val allowContacts: Boolean = true,
  val allowRepeatedCalls: Boolean = false,
  val blockHiddenNumbers: Boolean = false,
  val blockInternationalNumbers: Boolean = false,
)

/**
 * Registro de una llamada que fue cortada automáticamente por Antillamadas.
 * Se guarda solo dentro del teléfono para que puedas revisar qué se bloqueó.
 */
@Serializable
data class BlockedCallRecord(
  val id: String,
  val rawNumber: String,
  val formattedNumber: String,
  val reason: String,
  val matchedRuleId: String? = null,
  val timestampMillis: Long,
)

/**
 * Huella local anónima de una llamada desconocida (fuera de tus Contactos) que no fue bloqueada.
 * Permite detectar cuando un call center rota los últimos 4 dígitos sobre un mismo inicio de
 * 5 dígitos (ej. +56 9 3762 ••••) sin guardar el número telefónico completo de esa persona.
 */
@Serializable
data class ObservedPrefixHit(
  val prefix5: String,
  val callerFingerprint: String,
  val timestampMillis: Long,
)

/**
 * Resultado final tras evaluar una llamada entrante:
 * o la dejamos sonar (Allow) o la cortamos inmediatamente (Block).
 */
sealed interface FilterDecision {
  data class Allow(val reason: String) : FilterDecision

  data class Block(
    val reason: String,
    val matchedRuleId: String? = null,
  ) : FilterDecision
}

/**
 * Reglas preconfiguradas según la normativa de numeración de Subtel en Chile.
 */
object BuiltInChileRules {
  const val ID_PREFIX_809 = "builtin_cl_809"
  const val ID_PREFIX_600 = "builtin_cl_600"

  val prefix809 = FilterRule(
    id = ID_PREFIX_809,
    label = "Prefijo 809 (Spam comercial no solicitado)",
    pattern = "809",
    matchType = MatchType.PREFIX,
    kind = RuleKind.BLOCK,
    enabled = true,
    isBuiltIn = true,
  )

  val prefix600 = FilterRule(
    id = ID_PREFIX_600,
    label = "Prefijo 600 (Servicios masivos y cobranza)",
    pattern = "600",
    matchType = MatchType.PREFIX,
    kind = RuleKind.BLOCK,
    enabled = true,
    isBuiltIn = true,
  )

  fun defaults(): List<FilterRule> = listOf(prefix809, prefix600)
}
