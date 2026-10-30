package cl.antillamadas.app.ui.main

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.antillamadas.app.R
import cl.antillamadas.app.data.CommunitySyncResult
import cl.antillamadas.app.data.ImportResult
import cl.antillamadas.app.data.LocalSpamRepository
import cl.antillamadas.app.data.SpamStoreData
import cl.antillamadas.app.domain.BlockedCallRecord
import cl.antillamadas.app.domain.MatchType
import cl.antillamadas.app.domain.PhoneNumberNormalizer
import cl.antillamadas.app.domain.RuleKind
import cl.antillamadas.app.domain.SpamStatsCalculator
import cl.antillamadas.app.screening.AddTileOutcome
import cl.antillamadas.app.screening.AntillamadasTileService
import cl.antillamadas.app.screening.CallRoleHelper
import cl.antillamadas.app.screening.ContactsLookup
import cl.antillamadas.app.ui.components.GooglePillChip
import cl.antillamadas.app.ui.theme.AntillamadasTheme
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.launch

enum class AppTab(val title: String, val iconRes: Int) {
  STATS("Estadísticas", R.drawable.ic_stats),
  PROTECTION("Protección", R.drawable.ic_shield_check),
  HISTORY("Historial", R.drawable.ic_history),
  RULES("Reglas", R.drawable.ic_rules),
}

@Composable
fun AntillamadasScreen(
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val viewModel: AntillamadasViewModel = viewModel(
    factory = AntillamadasViewModel.factory(context),
  )
  val storeData by viewModel.storeState.collectAsStateWithLifecycle()
  val isSyncingCommunity by viewModel.isSyncingCommunity.collectAsStateWithLifecycle()

  var isRoleHeld by remember { mutableStateOf(CallRoleHelper.isCallScreeningRoleHeld(context)) }
  var hasContactsPermission by remember {
    mutableStateOf(ContactsLookup.hasContactsPermission(context))
  }

  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  // Cada vez que el usuario vuelve a la app desde ajustes o desde la cortina superior, refrescamos estados.
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        isRoleHeld = CallRoleHelper.isCallScreeningRoleHeld(context)
        hasContactsPermission = ContactsLookup.hasContactsPermission(context)
        AntillamadasTileService.requestSync(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val roleLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) {
    isRoleHeld = CallRoleHelper.isCallScreeningRoleHeld(context)
    AntillamadasTileService.requestSync(context)
    val message = if (isRoleHeld) {
      "Protección activada: Antillamadas ya filtra llamadas entrantes."
    } else {
      "Aún no se activó el permiso de filtrado en Android."
    }
    scope.launch { snackbarHostState.showSnackbar(message) }
  }

  val contactsPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasContactsPermission = granted
    if (!granted) {
      scope.launch {
        snackbarHostState.showSnackbar("Sin permiso de Contactos no se podrá verificar tu agenda.")
      }
    }
  }

  val exportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json"),
  ) { uri ->
    if (uri != null) {
      val saved = runCatching {
        val content = viewModel.exportRulesJson()
        context.contentResolver.openOutputStream(uri)?.use { stream ->
          stream.write(content.toByteArray(Charsets.UTF_8))
        }
      }.isSuccess
      scope.launch {
        snackbarHostState.showSnackbar(
          if (saved) "Reglas exportadas correctamente." else "No se pudo guardar el archivo."
        )
      }
    }
  }

  val importLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      val result = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
          readBoundedUtf8(stream, LocalSpamRepository.MAX_IMPORT_BYTES)
        }
        if (text == null) {
          ImportResult.Error("El archivo supera el tamaño máximo permitido (256 KB).")
        } else {
          viewModel.importRulesJson(text)
        }
      }.getOrElse {
        ImportResult.Error("No se pudo leer el archivo seleccionado.")
      }

      AntillamadasTileService.requestSync(context)
      val msg = when (result) {
        is ImportResult.Success ->
          "Reglas importadas (${result.totalCustomRules} reglas personalizadas activas)."
        is ImportResult.Error -> result.message
      }
      scope.launch { snackbarHostState.showSnackbar(msg) }
    }
  }

  AntillamadasContent(
    storeData = storeData,
    isRoleHeld = isRoleHeld,
    hasContactsPermission = hasContactsPermission,
    snackbarHostState = snackbarHostState,
    onRequestScreeningRole = {
      val intent = CallRoleHelper.createRequestRoleIntent(context)
      if (intent != null) {
        roleLauncher.launch(intent)
      } else {
        scope.launch {
          snackbarHostState.showSnackbar("Este dispositivo no soporta el rol de filtro de llamadas.")
        }
      }
    },
    onRequestContactsPermission = {
      contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    },
    onToggleProtectionEnabled = { enabled ->
      viewModel.setProtectionEnabled(enabled)
      AntillamadasTileService.requestSync(context)
      scope.launch {
        snackbarHostState.showSnackbar(
          if (enabled) {
            "Protección reanudada."
          } else {
            "Protección en pausa temporal (puedes reanudarla aquí o en la cortina superior)."
          }
        )
      }
    },
    onRequestAddQuickTile = {
      AntillamadasTileService.requestAddTileToQuickSettings(context) { outcome ->
        val feedback = when (outcome) {
          AddTileOutcome.ADDED -> "Botón Antillamadas agregado a tu cortina superior."
          AddTileOutcome.ALREADY_ADDED -> "El botón ya está en tu cortina superior de Android."
          AddTileOutcome.DISMISSED -> null
          AddTileOutcome.MANUAL_INSTRUCTIONS_NEEDED ->
            "Desliza la cortina superior de Android, toca el lápiz de editar y arrastra \"Antillamadas\"."
        }
        if (feedback != null) {
          scope.launch { snackbarHostState.showSnackbar(feedback) }
        }
      }
    },
    onToggleRule = viewModel::toggleRule,
    onAddRule = { label, pattern, matchType, kind ->
      val ok = viewModel.addRule(label, pattern, matchType, kind)
      if (ok) {
        scope.launch { snackbarHostState.showSnackbar("Regla guardada.") }
      }
      ok
    },
    onEditRule = { ruleId, label, pattern, matchType, kind ->
      val ok = viewModel.updateRule(ruleId, label, pattern, matchType, kind)
      if (ok) {
        scope.launch { snackbarHostState.showSnackbar("Regla actualizada.") }
      }
      ok
    },
    onDeleteRule = viewModel::deleteRule,
    onToggleCommunitySync = { enabled ->
      viewModel.setCommunitySyncEnabled(enabled)
      scope.launch {
        snackbarHostState.showSnackbar(
          if (enabled) "Base comunitaria activada." else "Base comunitaria desactivada."
        )
      }
    },
    onSyncCommunity = {
      viewModel.syncCommunityDatabase { result ->
        scope.launch {
          val msg = when (result) {
            is CommunitySyncResult.Success ->
              "Lista comunitaria actualizada (${result.entries.size} números activos)."
            is CommunitySyncResult.Error -> result.message
          }
          snackbarHostState.showSnackbar(msg)
        }
      }
    },
    onToggleAllowContacts = viewModel::setAllowContacts,
    onToggleAllowRepeatedCalls = viewModel::setAllowRepeatedCalls,
    onToggleBlockHidden = viewModel::setBlockHiddenNumbers,
    onToggleBlockInternational = viewModel::setBlockInternationalNumbers,
    onBlockSuggestedPrefix = { prefix5 ->
      if (viewModel.blockSuggestedPrefix(prefix5)) {
        scope.launch {
          snackbarHostState.showSnackbar("Prefijo +56 ${prefix5.first()} ${prefix5.substring(1)} bloqueado.")
        }
      }
    },
    onDismissSuggestion = viewModel::dismissPrefixSuggestion,
    onExportRules = { exportLauncher.launch("antillamadas_reglas.json") },
    onImportRules = { importLauncher.launch(arrayOf("application/json", "*/*")) },
    onCopyForSernac = { rawNumber ->
      val cleanDigits = PhoneNumberNormalizer.formatForSernac(rawNumber)
      if (cleanDigits.isNotEmpty()) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Número spam SERNAC", cleanDigits))
        scope.launch {
          snackbarHostState.showSnackbar("Copiado: $cleanDigits (listo para pegar en No Molestar SERNAC)")
        }
      }
    },
    onAllowNumberFromHistory = { rawNumber ->
      if (viewModel.allowNumberFromHistory(rawNumber)) {
        scope.launch {
          snackbarHostState.showSnackbar("Número agregado a tu Lista Blanca.")
        }
      }
    },
    onDeleteHistoryItem = viewModel::deleteHistoryItem,
    onClearHistory = viewModel::clearHistory,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntillamadasContent(
  storeData: SpamStoreData,
  isRoleHeld: Boolean,
  hasContactsPermission: Boolean,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
  onRequestScreeningRole: () -> Unit = {},
  onRequestContactsPermission: () -> Unit = {},
  onToggleProtectionEnabled: (Boolean) -> Unit = {},
  onRequestAddQuickTile: () -> Unit = {},
  isSyncingCommunity: Boolean = false,
  onToggleRule: (String, Boolean) -> Unit = { _, _ -> },
  onAddRule: (String, String, MatchType, RuleKind) -> Boolean = { _, _, _, _ -> true },
  onEditRule: (String, String, String, MatchType, RuleKind) -> Boolean = { _, _, _, _, _ -> true },
  onDeleteRule: (String) -> Unit = {},
  onToggleCommunitySync: (Boolean) -> Unit = {},
  onSyncCommunity: () -> Unit = {},
  onToggleAllowContacts: (Boolean) -> Unit = {},
  onToggleAllowRepeatedCalls: (Boolean) -> Unit = {},
  onToggleBlockHidden: (Boolean) -> Unit = {},
  onToggleBlockInternational: (Boolean) -> Unit = {},
  onBlockSuggestedPrefix: (String) -> Unit = {},
  onDismissSuggestion: (String) -> Unit = {},
  onExportRules: () -> Unit = {},
  onImportRules: () -> Unit = {},
  onCopyForSernac: (String) -> Unit = {},
  onAllowNumberFromHistory: (String) -> Unit = {},
  onDeleteHistoryItem: (String) -> Unit = {},
  onClearHistory: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var selectedTab by rememberSaveable { mutableStateOf(AppTab.PROTECTION) }
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

  val hasSmartSuggestions = remember(storeData) {
    SpamStatsCalculator.compute(
      blockedHistory = storeData.blockedHistory,
      rules = storeData.rules,
      settings = storeData.settings,
      observedPrefixHits = storeData.observedPrefixHits,
      dismissedPrefixes = storeData.dismissedSuggestedPrefixes,
    ).smartSuggestions.isNotEmpty()
  }

  Scaffold(
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      TopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
          ) {
            Text(
              text = "Antillamadas",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            GooglePillChip(
              text = if (storeData.settings.protectionEnabled) "Chile" else "En pausa",
              containerColor = if (storeData.settings.protectionEnabled) {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                MaterialTheme.colorScheme.secondaryContainer
              },
              contentColor = if (storeData.settings.protectionEnabled) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.onSecondaryContainer
              },
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
          titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
      )
    },
    bottomBar = {
      NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
      ) {
        AppTab.entries.forEach { tab ->
          NavigationBarItem(
            selected = selectedTab == tab,
            onClick = { selectedTab = tab },
            icon = {
              when {
                tab == AppTab.HISTORY && storeData.blockedHistory.isNotEmpty() -> {
                  BadgedBox(
                    badge = {
                      Badge { Text(storeData.blockedHistory.size.toString()) }
                    }
                  ) {
                    Icon(
                      painter = painterResource(tab.iconRes),
                      contentDescription = tab.title,
                    )
                  }
                }
                tab == AppTab.STATS && hasSmartSuggestions -> {
                  BadgedBox(badge = { Badge() }) {
                    Icon(
                      painter = painterResource(tab.iconRes),
                      contentDescription = tab.title,
                    )
                  }
                }
                else -> {
                  Icon(
                    painter = painterResource(tab.iconRes),
                    contentDescription = tab.title,
                  )
                }
              }
            },
            label = { Text(tab.title) },
          )
        }
      }
    },
  ) { innerPadding ->
    when (selectedTab) {
      AppTab.STATS -> StatsTab(
        storeData = storeData,
        onBlockSuggestedPrefix = onBlockSuggestedPrefix,
        onDismissSuggestion = onDismissSuggestion,
        modifier = Modifier.padding(innerPadding),
      )

      AppTab.PROTECTION -> ProtectionTab(
        storeData = storeData,
        isRoleHeld = isRoleHeld,
        hasContactsPermission = hasContactsPermission,
        onRequestScreeningRole = onRequestScreeningRole,
        onRequestContactsPermission = onRequestContactsPermission,
        onToggleProtectionEnabled = onToggleProtectionEnabled,
        onRequestAddQuickTile = onRequestAddQuickTile,
        onToggleRule = onToggleRule,
        onToggleAllowContacts = onToggleAllowContacts,
        onToggleAllowRepeatedCalls = onToggleAllowRepeatedCalls,
        onToggleBlockHidden = onToggleBlockHidden,
        onToggleBlockInternational = onToggleBlockInternational,
        modifier = Modifier.padding(innerPadding),
      )

      AppTab.HISTORY -> HistoryTab(
        blockedCalls = storeData.blockedHistory,
        onCopyForSernac = onCopyForSernac,
        onAllowNumber = onAllowNumberFromHistory,
        onDeleteRecord = onDeleteHistoryItem,
        onClearAll = onClearHistory,
        modifier = Modifier.padding(innerPadding),
      )

      AppTab.RULES -> RulesTab(
        storeData = storeData,
        isSyncingCommunity = isSyncingCommunity,
        onToggleRule = onToggleRule,
        onAddRule = onAddRule,
        onEditRule = onEditRule,
        onDeleteRule = onDeleteRule,
        onToggleCommunitySync = onToggleCommunitySync,
        onSyncCommunity = onSyncCommunity,
        onExportRules = onExportRules,
        onImportRules = onImportRules,
        modifier = Modifier.padding(innerPadding),
      )
    }
  }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AntillamadasActivePreview() {
  AntillamadasTheme(dynamicColor = false) {
    AntillamadasContent(
      storeData = SpamStoreData(
        blockedHistory = listOf(
          BlockedCallRecord(
            id = "1",
            rawNumber = "+56809123456",
            formattedNumber = "809 123 456",
            reason = "Prefijo 809 (Spam comercial no solicitado)",
            timestampMillis = 1793100000000L,
          )
        )
      ),
      isRoleHeld = true,
      hasContactsPermission = true,
    )
  }
}

/**
 * Lee un flujo de entrada con un tope estricto de bytes para evitar quedarnos sin memoria (OOM)
 * si el usuario selecciona accidentalmente un archivo gigante.
 */
private fun readBoundedUtf8(stream: InputStream, maxBytes: Int): String? {
  val output = ByteArrayOutputStream()
  val buffer = ByteArray(8 * 1024)
  var totalRead = 0
  while (true) {
    val count = stream.read(buffer)
    if (count < 0) break
    totalRead += count
    if (totalRead > maxBytes) return null
    output.write(buffer, 0, count)
  }
  return output.toString(Charsets.UTF_8.name())
}
