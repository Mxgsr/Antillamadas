package cl.antillamadas.app.domain

/**
 * Representa un número telefónico ya limpio y clasificado.
 *
 * Ejemplo en Chile:
 *   raw = "+56 600 600 1000"
 *   cleaned = "+566006001000"
 *   nationalDigits = "6006001000"
 *   isChilean = true
 */
data class NormalizedPhone(
  val raw: String,
  val cleaned: String,
  val nationalDigits: String?,
  val isChilean: Boolean,
  val isHidden: Boolean,
)

object PhoneNumberNormalizer {

  private const val CHILE_COUNTRY_CODE = "56"

  /**
   * Limpia un número entrante y separa el código de país (+56) para poder comparar
   * prefijos chilenos sin confundirlos con números extranjeros (como España +34 600...).
   */
  fun normalize(rawInput: String?): NormalizedPhone {
    val raw = rawInput?.trim().orEmpty()
    if (raw.isEmpty()) {
      return hiddenPhone(raw)
    }

    // Conservamos únicamente dígitos y un signo '+' si está al inicio.
    val digitsAndPlus = buildString {
      for (char in raw) {
        if (char.isDigit()) {
          append(char)
        } else if (char == '+' && isEmpty()) {
          append(char)
        }
      }
    }

    // Si el operador envía textos como "unknown", "private" o "-1" sin un número real:
    val digitCount = digitsAndPlus.count { it.isDigit() }
    if (digitCount < 3) {
      return hiddenPhone(raw)
    }

    // Si comienza con "00" (prefijo de salida internacional), lo estandarizamos a "+".
    val standardized = if (digitsAndPlus.startsWith("00")) {
      "+" + digitsAndPlus.removePrefix("00")
    } else {
      digitsAndPlus
    }

    return when {
      // Caso 1: Viene con formato internacional chileno "+56..."
      standardized.startsWith("+$CHILE_COUNTRY_CODE") -> {
        val national = standardized.removePrefix("+$CHILE_COUNTRY_CODE")
        NormalizedPhone(
          raw = raw,
          cleaned = standardized,
          nationalDigits = national.ifEmpty { null },
          isChilean = true,
          isHidden = national.isEmpty(),
        )
      }

      // Caso 2: Viene con '+' de otro país (ej. +34 España, +1 EE.UU., +54 Argentina)
      standardized.startsWith("+") -> {
        NormalizedPhone(
          raw = raw,
          cleaned = standardized,
          nationalDigits = null,
          isChilean = false,
          isHidden = false,
        )
      }

      // Caso 3: Viene sin '+', pero empieza con "56" y tiene largo internacional (>= 11 dígitos, ej. 56809123456).
      // En Chile ningún código de área fijo ni móvil comienza con 56 (Antofagasta es 55, Iquique es 57).
      standardized.startsWith(CHILE_COUNTRY_CODE) && standardized.length >= 11 -> {
        val national = standardized.removePrefix(CHILE_COUNTRY_CODE)
        NormalizedPhone(
          raw = raw,
          cleaned = "+$standardized",
          nationalDigits = national,
          isChilean = true,
          isHidden = false,
        )
      }

      // Caso 4: Viene directo como número nacional chileno (ej. "6006001000", "809123456", "912345678")
      else -> {
        NormalizedPhone(
          raw = raw,
          cleaned = standardized,
          nationalDigits = standardized,
          isChilean = true,
          isHidden = false,
        )
      }
    }
  }

  /**
   * Formatea un número para mostrarlo fácil de leer en la pantalla de Historial.
   */
  fun formatForDisplay(rawInput: String?): String {
    val phone = normalize(rawInput)
    if (phone.isHidden) return "Número oculto / privado"
    if (!phone.isChilean) return phone.cleaned

    val nat = phone.nationalDigits ?: return phone.cleaned
    return when {
      nat.startsWith("600") && (nat.length == 9 || nat.length == 10) ->
        "600 ${nat.substring(3, 6)} ${nat.substring(6)}"
      nat.startsWith("809") && (nat.length == 9 || nat.length == 10) ->
        "809 ${nat.substring(3, 6)} ${nat.substring(6)}"
      nat.startsWith("9") && nat.length == 9 ->
        "+56 9 ${nat.substring(1, 5)} ${nat.substring(5)}"
      nat.startsWith("2") && nat.length == 9 ->
        "+56 2 ${nat.substring(1, 5)} ${nat.substring(5)}"
      else -> "+56 $nat"
    }
  }

  /**
   * Clave única canónica para agrupar y contar llamadas del mismo número aunque un operador
   * lo envíe como "6006001000" y otro como "+56 600 600 1000".
   */
  fun canonicalKey(rawInput: String?): String {
    val phone = normalize(rawInput)
    if (phone.isHidden) return ""
    return if (phone.isChilean && !phone.nationalDigits.isNullOrEmpty()) {
      "+$CHILE_COUNTRY_CODE${phone.nationalDigits}"
    } else {
      phone.cleaned
    }
  }

  /**
   * Devuelve el número limpio sin espacios (ej. "6006001000" o "809123456"),
   * ideal para pegarlo directo en el formulario "No Molestar" del SERNAC.
   */
  fun formatForSernac(rawInput: String?): String {
    val phone = normalize(rawInput)
    if (phone.isHidden) return ""
    return if (phone.isChilean && !phone.nationalDigits.isNullOrEmpty()) {
      phone.nationalDigits
    } else {
      phone.cleaned
    }
  }

  private fun hiddenPhone(raw: String) = NormalizedPhone(
    raw = raw,
    cleaned = "",
    nationalDigits = null,
    isChilean = false,
    isHidden = true,
  )
}
