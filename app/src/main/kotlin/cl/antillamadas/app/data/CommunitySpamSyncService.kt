package cl.antillamadas.app.data

import cl.antillamadas.app.domain.CommunitySpamEntry
import cl.antillamadas.app.domain.DEFAULT_COMMUNITY_URL
import cl.antillamadas.app.domain.PhoneNumberNormalizer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface CommunitySyncResult {
  data class Success(val entries: List<CommunitySpamEntry>) : CommunitySyncResult
  data class Error(val message: String) : CommunitySyncResult
}

object CommunitySpamSyncService {

  private const val MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024 // 2 MB tope de seguridad
  private const val TIMEOUT_MILLIS = 8000
  private const val MAX_REDIRECTS = 3

  suspend fun fetchAndParse(
    urlString: String = DEFAULT_COMMUNITY_URL,
  ): CommunitySyncResult = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
      var currentUrl = urlString.trim()
      var redirectCount = 0

      // Manejamos redirecciones HTTP (ej. 301/302 de GitHub o acortadores)
      while (true) {
        val url = URL(currentUrl)
        // Solo HTTPS: la lista decide qué llamadas se cortan, no puede viajar sin cifrar.
        if (url.protocol != "https") {
          return@withContext CommunitySyncResult.Error("La lista solo se descarga por conexión segura (https).")
        }
        val conn = (url.openConnection() as HttpURLConnection).apply {
          connectTimeout = TIMEOUT_MILLIS
          readTimeout = TIMEOUT_MILLIS
          instanceFollowRedirects = true
          setRequestProperty("User-Agent", "Antillamadas-Chile/2.2")
          setRequestProperty("Accept", "text/csv, text/plain, */*")
        }
        connection = conn

        val code = conn.responseCode
        if (code in 300..399) {
          val location = conn.getHeaderField("Location")
          if (!location.isNullOrBlank() && redirectCount < MAX_REDIRECTS) {
            currentUrl = URL(url, location).toString()
            redirectCount++
            conn.disconnect()
            continue
          }
          return@withContext CommunitySyncResult.Error("Demasiadas redirecciones al descargar la lista.")
        }

        if (code !in 200..299) {
          return@withContext CommunitySyncResult.Error("El servidor respondió con código $code al descargar la lista.")
        }
        break
      }

      val conn = connection ?: return@withContext CommunitySyncResult.Error("No se pudo iniciar la conexión.")
      val stream = conn.inputStream
      val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
      val sb = StringBuilder()
      var totalBytes = 0

      val buffer = CharArray(4096)
      while (true) {
        val read = reader.read(buffer)
        if (read < 0) break
        totalBytes += read * 2 // Estimación UTF-16
        if (totalBytes > MAX_DOWNLOAD_BYTES) {
          return@withContext CommunitySyncResult.Error("La lista supera el tamaño máximo de seguridad (2 MB).")
        }
        sb.append(buffer, 0, read)
      }

      val entries = parseCsv(sb.toString())
      if (entries.isEmpty()) {
        CommunitySyncResult.Error("No se encontraron números válidos en la lista descargada.")
      } else {
        CommunitySyncResult.Success(entries)
      }
    } catch (e: Exception) {
      CommunitySyncResult.Error(
        when {
          e is java.net.UnknownHostException -> "Sin conexión a internet. Revisa tu red."
          e is java.net.SocketTimeoutException -> "Tiempo de espera agotado al conectar con el servidor."
          else -> "Error de red: ${e.localizedMessage ?: "imposible descargar"}"
        }
      )
    } finally {
      connection?.disconnect()
    }
  }

  /**
   * Parser puro y sin efectos secundarios para pruebas unitarias.
   * Soporta encabezados como "PhoneNumber,name", comillas y motivos descriptivos.
   */
  fun parseCsv(csvText: String): List<CommunitySpamEntry> {
    val lines = csvText.lineSequence()
    val result = LinkedHashMap<String, CommunitySpamEntry>() // Preserva orden y deduplica por clave canónica

    var isFirstLine = true
    for (rawLine in lines) {
      val line = rawLine.trim()
      if (line.isEmpty() || line.startsWith("#")) continue

      val tokens = splitCsvLine(line)
      if (tokens.isEmpty()) continue

      val first = tokens[0]
      if (isFirstLine) {
        isFirstLine = false
        val lower = first.lowercase()
        if (lower.contains("phone") || lower.contains("numero") || lower.contains("número")) {
          continue // Descartamos la fila de encabezado
        }
      }

      val phoneRaw = first
      val labelRaw = if (tokens.size > 1) tokens[1] else "Spam reportado"

      val canonical = PhoneNumberNormalizer.canonicalKey(phoneRaw)
      if (canonical.length >= 8) { // Al menos código de país o número nacional
        val cleanLabel = labelRaw
          .filterNot { it.isISOControl() }
          .trim()
          .take(40)
          .ifEmpty { "Spam reportado" }

        if (!result.containsKey(canonical)) {
          result[canonical] = CommunitySpamEntry(
            canonicalNumber = canonical,
            rawNumber = phoneRaw,
            label = cleanLabel,
          )
        }
      }
    }

    return result.values.toList()
  }

  private fun splitCsvLine(line: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false

    for (c in line) {
      when {
        c == '\"' -> inQuotes = !inQuotes
        c == ',' && !inQuotes -> {
          tokens.add(current.toString().trim())
          current.clear()
        }
        else -> current.append(c)
      }
    }
    tokens.add(current.toString().trim())
    return tokens
  }
}
