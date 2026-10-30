package cl.antillamadas.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunitySpamSyncServiceTest {

  @Test
  fun `parsea correctamente formato csv de SpamChile ignorando encabezados y deduplicando`() {
    val sampleCsv = """
      # Lista comunitaria de prueba
      PhoneNumber,name
      56442760172,Fraude SMS
      +56442760172,Fraude SMS
      +56965130021,"Robocaller Cobranza"
      +56443302101,Tottus
      
      invalido,No es numero
      +56990066022,Agente Virtual Claro
    """.trimIndent()

    val entries = CommunitySpamSyncService.parseCsv(sampleCsv)

    // Debe haber 4 números únicos válidos (56442760172 está duplicado y se deduplica):
    assertEquals(4, entries.size)

    val entry1 = entries.first { it.rawNumber.contains("442760172") }
    assertEquals("+56442760172", entry1.canonicalNumber)
    assertEquals("Fraude SMS", entry1.label)

    val entryRobocaller = entries.first { it.canonicalNumber == "+56965130021" }
    assertEquals("Robocaller Cobranza", entryRobocaller.label)

    assertTrue(entries.any { it.canonicalNumber == "+56443302101" && it.label == "Tottus" })
    assertTrue(entries.any { it.canonicalNumber == "+56990066022" })
  }

  @Test
  fun `rechaza descargar la lista por http sin cifrar`() = runTest {
    val result = CommunitySpamSyncService.fetchAndParse("http://ejemplo.invalid/lista.csv")

    assertTrue(result is CommunitySyncResult.Error)
  }
}
