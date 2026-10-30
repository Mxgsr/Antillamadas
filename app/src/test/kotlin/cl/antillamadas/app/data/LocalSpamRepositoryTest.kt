package cl.antillamadas.app.data

import cl.antillamadas.app.domain.BuiltInChileRules
import cl.antillamadas.app.domain.DEFAULT_COMMUNITY_URL
import cl.antillamadas.app.domain.MatchType
import cl.antillamadas.app.domain.RuleKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pruebas automáticas del Módulo 2: verifican que las reglas, el historial y
 * el respaldo JSON funcionen 100% offline y persistan al cerrar y reabrir la app.
 */
class LocalSpamRepositoryTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun `persiste reglas personalizadas y cambios de interruptores al reiniciar`() {
    val file = File(tempFolder.root, "antillamadas_store.json")

    // Primera sesión de la app: apagamos el 600 y agregamos un call center móvil
    val repo1 = LocalSpamRepository(file)
    repo1.setRuleEnabled(BuiltInChileRules.ID_PREFIX_600, enabled = false)
    val added = repo1.addCustomRule(
      label = "Call center cobranza",
      pattern = "+56 9 3762*",
      matchType = MatchType.WILDCARD,
      kind = RuleKind.BLOCK,
    )
    assertNotNull(added)

    // Simulamos reiniciar el teléfono creando un nuevo repositorio que lee el mismo archivo
    val repo2 = LocalSpamRepository(file)
    val loadedRules = repo2.snapshot().rules

    val rule600 = loadedRules.first { it.id == BuiltInChileRules.ID_PREFIX_600 }
    assertFalse("El prefijo 600 debía recordar que quedó apagado", rule600.enabled)
    assertTrue(
      "Debía conservar la regla personalizada en disco",
      loadedRules.any { it.label == "Call center cobranza" },
    )
  }

  @Test
  fun `guarda historial ordenado desde la llamada mas reciente y respeta el limite maximo`() {
    val file = File(tempFolder.root, "history_store.json")
    val repo = LocalSpamRepository(file)

    val limit = LocalSpamRepository.MAX_HISTORY_ITEMS
    for (i in 1..(limit + 5)) {
      repo.recordBlockedCall(
        rawNumber = "+56809000$i",
        reason = "Prefijo 809",
        timestampMillis = i.toLong(),
      )
    }

    val history = repo.snapshot().blockedHistory
    assertEquals(limit, history.size)
    // El primer elemento debe ser el último que entró (limit + 5)
    assertEquals("+56809000${limit + 5}", history.first().rawNumber)
  }

  @Test
  fun `exporta e importa reglas sin exponer el historial privado de llamadas`() {
    val sourceFile = File(tempFolder.root, "phone_a.json")
    val targetFile = File(tempFolder.root, "phone_b.json")

    val phoneA = LocalSpamRepository(sourceFile)
    phoneA.updateSettings { it.copy(blockHiddenNumbers = true) }
    phoneA.addCustomRule(
      label = "Mi Banco (Excepción)",
      pattern = "600 320 3000",
      matchType = MatchType.EXACT,
      kind = RuleKind.ALLOW,
    )
    phoneA.recordBlockedCall("+56809999888", "Prefijo 809")

    val exportedJson = phoneA.exportRulesJson()

    // Privacidad: el número que nos llamó NO debe ir dentro del archivo de reglas exportado
    assertFalse(exportedJson.contains("809999888"))

    // Importamos en un segundo teléfono en blanco
    val phoneB = LocalSpamRepository(targetFile)
    val result = phoneB.importRulesJson(exportedJson)

    assertTrue(result is ImportResult.Success)
    assertTrue(phoneB.snapshot().settings.blockHiddenNumbers)
    assertTrue(phoneB.snapshot().rules.any { it.label == "Mi Banco (Excepción)" })
    assertTrue("El teléfono B comienza con su propio historial vacío", phoneB.snapshot().blockedHistory.isEmpty())
  }

  @Test
  fun `devuelve error controlado si se intenta importar un archivo invalido`() {
    val repo = LocalSpamRepository(File(tempFolder.root, "invalid_test.json"))
    val result = repo.importRulesJson("{ esto no es un json valido }")
    assertTrue(result is ImportResult.Error)
  }

  @Test
  fun `modifica regla personalizada existente y conserva el cambio al reiniciar`() {
    val file = File(tempFolder.root, "edit_rule_store.json")
    val repo1 = LocalSpamRepository(file)

    val created = repo1.addCustomRule(
      label = "Call center con error",
      pattern = "93769",
      matchType = MatchType.PREFIX,
      kind = RuleKind.BLOCK,
    )
    assertNotNull(created)

    val updated = repo1.updateCustomRule(
      ruleId = created!!.id,
      label = "Call center corregido",
      pattern = "93762*",
      matchType = MatchType.WILDCARD,
      kind = RuleKind.BLOCK,
    )
    assertTrue(updated)

    // Verificamos leyendo nuevamente desde disco que la regla modificada persiste sin duplicarse:
    val repo2 = LocalSpamRepository(file)
    val customRules = repo2.snapshot().rules.filter { !it.isBuiltIn }
    assertEquals(1, customRules.size)
    assertEquals(created.id, customRules.first().id)
    assertEquals("Call center corregido", customRules.first().label)
    assertEquals("93762*", customRules.first().pattern)
    assertEquals(MatchType.WILDCARD, customRules.first().matchType)
  }

  @Test
  fun `endurece importacion json descartando comodines puros corrigiendo ids duplicados y limitando tamano`() {
    val repo = LocalSpamRepository(File(tempFolder.root, "hardened_store.json"))

    // 1. Archivo gigante (> 256 KB) es rechazado de inmediato:
    val hugePayload = "a".repeat(LocalSpamRepository.MAX_IMPORT_BYTES + 100)
    assertTrue(repo.importRulesJson(hugePayload) is ImportResult.Error)

    // 2. JSON con comodín puro ("*"), saltos de línea en nombre e IDs repetidos:
    val craftedJson = """
      {
        "formatVersion": 1,
        "exportedAtMillis": 1700000000000,
        "settings": { "allowContacts": true, "blockHiddenNumbers": false, "blockInternationalNumbers": false },
        "rules": [
          { "id": "dup_id", "label": "Trampa permitir todo", "pattern": "*", "matchType": "WILDCARD", "kind": "ALLOW", "enabled": true, "isBuiltIn": false },
          { "id": "dup_id", "label": "Call\ncenter\t1", "pattern": "93762*", "matchType": "WILDCARD", "kind": "BLOCK", "enabled": true, "isBuiltIn": false },
          { "id": "dup_id", "label": "Call center 2", "pattern": "22914*", "matchType": "WILDCARD", "kind": "BLOCK", "enabled": true, "isBuiltIn": false }
        ]
      }
    """.trimIndent()

    val result = repo.importRulesJson(craftedJson)
    assertTrue(result is ImportResult.Success)

    val customRules = repo.snapshot().rules.filter { !it.isBuiltIn }
    assertEquals("El comodín puro '*' debía descartarse", 2, customRules.size)
    assertEquals("Los IDs deben quedar únicos", 2, customRules.map { it.id }.toSet().size)
    assertEquals("Call center 1", customRules.first().label)
  }

  @Test
  fun `registra prefijos desconocidos con hash anonimo sin guardar el numero completo`() {
    val storeFile = File(tempFolder.root, "anon_prefix_store.json")
    val repo = LocalSpamRepository(storeFile)

    repo.recordObservedUnknownCall("+56 9 3762 8491", timestampMillis = 1000L)
    repo.recordObservedUnknownCall("+56 9 3762 1122", timestampMillis = 2000L)

    val hits = repo.snapshot().observedPrefixHits
    assertEquals(2, hits.size)
    assertEquals("93762", hits.first().prefix5)
    // Sus fingerprints deben ser distintos porque son 2 números diferentes:
    assertEquals(2, hits.map { it.callerFingerprint }.toSet().size)

    // Privacidad: el archivo en disco NO contiene los números completos 37628491 ni 37621122:
    val rawDiskJson = storeFile.readText(Charsets.UTF_8)
    assertFalse(rawDiskJson.contains("37628491"))
    assertFalse(rawDiskJson.contains("37621122"))
  }

  @Test
  fun `persiste lista comunitaria pero no la incluye en el respaldo json privado del usuario`() {
    val storeFile = File(tempFolder.root, "comm_store.json")
    val repo = LocalSpamRepository(storeFile)

    val sampleEntries = listOf(
      cl.antillamadas.app.domain.CommunitySpamEntry(
        canonicalNumber = "+56442760172",
        rawNumber = "+56442760172",
        label = "Fraude SMS",
      )
    )

    repo.setCommunitySyncEnabled(true)
    repo.updateCommunitySpamList(sampleEntries, timestampMillis = 1700000000000L)

    // Al recargar desde disco:
    val reloaded = LocalSpamRepository(storeFile)
    assertTrue(reloaded.snapshot().settings.communitySyncEnabled)
    assertEquals(1, reloaded.snapshot().communitySpamList.size)
    assertEquals(1700000000000L, reloaded.snapshot().settings.lastCommunitySyncMillis)

    // Al exportar respaldo de reglas para compartir o guardar, NO debe incluir la lista comunitaria (pesaría megabytes innecesarios):
    val exportedJson = repo.exportRulesJson()
    assertFalse("El respaldo personal no debe incluir entradas comunitarias", exportedJson.contains("442760172"))
  }

  @Test
  fun `importar un respaldo ajeno no cambia la lista comunitaria ni pausa la proteccion`() {
    val repo = LocalSpamRepository(File(tempFolder.root, "import_settings_store.json"))
    repo.updateSettings { it.copy(blockHiddenNumbers = false) }

    // Respaldo manipulado: apunta a otra lista, finge consentimiento previo y pausa la protección
    val craftedJson = """
      {
        "formatVersion": 2,
        "exportedAtMillis": 1700000000000,
        "settings": {
          "protectionEnabled": false,
          "blockHiddenNumbers": true,
          "communitySyncEnabled": true,
          "communitySourceUrl": "https://ejemplo-malicioso.invalid/lista.csv",
          "lastCommunitySyncMillis": 1700000000000
        },
        "rules": []
      }
    """.trimIndent()

    assertTrue(repo.importRulesJson(craftedJson) is ImportResult.Success)

    val settings = repo.snapshot().settings
    assertEquals(DEFAULT_COMMUNITY_URL, settings.communitySourceUrl)
    assertFalse("La base comunitaria solo se activa desde la app", settings.communitySyncEnabled)
    assertEquals(null, settings.lastCommunitySyncMillis)
    assertTrue("Un respaldo no debe pausar la protección", settings.protectionEnabled)
    assertTrue("Los ajustes normales sí se importan", settings.blockHiddenNumbers)
  }
}
