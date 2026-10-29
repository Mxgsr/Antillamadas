package cl.antillamadas.app.ui.main

import cl.antillamadas.app.data.LocalSpamRepository
import cl.antillamadas.app.domain.FilterDecision
import cl.antillamadas.app.domain.MatchType
import cl.antillamadas.app.domain.RuleKind
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AntillamadasViewModelTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun `permite agregar excepcion desde historial y simular llamada`() {
    val repo = LocalSpamRepository(File(tempFolder.root, "vm_store.json"))
    val viewModel = AntillamadasViewModel(repo)

    // Al principio, un 600 se bloquea:
    assertTrue(viewModel.simulateCall("+56 600 320 3000") is FilterDecision.Block)

    // Si el usuario pulsa "Permitir siempre este número" en el historial:
    assertTrue(viewModel.allowNumberFromHistory("+56 600 320 3000"))

    // Ahora ese 600 específico pasa como excepción, mientras otros 600 siguen bloqueados:
    assertTrue(viewModel.simulateCall("+56 600 320 3000") is FilterDecision.Allow)
    assertTrue(viewModel.simulateCall("+56 600 999 9999") is FilterDecision.Block)
  }

  @Test
  fun `agrega patron personalizado y bloquea llamadas que coinciden`() {
    val repo = LocalSpamRepository(File(tempFolder.root, "vm_custom.json"))
    val viewModel = AntillamadasViewModel(repo)

    val added = viewModel.addRule(
      label = "Call center 93762",
      pattern = "93762",
      matchType = MatchType.PREFIX,
      kind = RuleKind.BLOCK,
    )
    assertTrue(added)
    assertTrue(viewModel.simulateCall("+56 9 3762 4455") is FilterDecision.Block)
  }
}
