package cl.antillamadas.app.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.antillamadas.app.R
import cl.antillamadas.app.data.LocalSpamRepository
import cl.antillamadas.app.data.SpamStoreData
import cl.antillamadas.app.domain.CallFilterEngine
import cl.antillamadas.app.domain.FilterRule
import cl.antillamadas.app.domain.MatchType
import cl.antillamadas.app.domain.RuleKind
import cl.antillamadas.app.ui.components.GooglePillChip
import cl.antillamadas.app.ui.components.GoogleSectionHeader
import cl.antillamadas.app.ui.components.TonalIconBadge
import cl.antillamadas.app.ui.components.googleSegmentedShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RulesTab(
  storeData: SpamStoreData,
  isSyncingCommunity: Boolean = false,
  onToggleRule: (String, Boolean) -> Unit,
  onAddRule: (label: String, pattern: String, matchType: MatchType, kind: RuleKind) -> Boolean,
  onEditRule: (ruleId: String, label: String, pattern: String, matchType: MatchType, kind: RuleKind) -> Boolean,
  onDeleteRule: (String) -> Unit,
  onToggleCommunitySync: (Boolean) -> Unit,
  onSyncCommunity: () -> Unit,
  onExportRules: () -> Unit,
  onImportRules: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showCreateDialog by rememberSaveable { mutableStateOf(false) }
  var showConsentDialog by rememberSaveable { mutableStateOf(false) }
  var editingRuleId by rememberSaveable { mutableStateOf<String?>(null) }

  val customBlockRules = storeData.rules.filter { !it.isBuiltIn && it.kind == RuleKind.BLOCK }
  val allowRules = storeData.rules.filter { !it.isBuiltIn && it.kind == RuleKind.ALLOW }
  val ruleBeingEdited = storeData.rules.find { !it.isBuiltIn && it.id == editingRuleId }

  val lastSyncFormatted = remember(storeData.settings.lastCommunitySyncMillis) {
    storeData.settings.lastCommunitySyncMillis?.let {
      SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.forLanguageTag("es-CL")).format(Date(it))
    }
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    // Acción principal estilo Google Pill Button
    item {
      Button(
        onClick = { showCreateDialog = true },
        shape = CircleShape,
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 20.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_add),
          contentDescription = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Agregar patrón o excepción")
      }
    }

    // Sección 1: Patrones personalizados bloqueados
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GoogleSectionHeader(title = "Patrones repetidos bloqueados")
        if (customBlockRules.isEmpty()) {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
          ) {
            Column(modifier = Modifier.padding(18.dp)) {
              Text(
                text = "Sin patrones personalizados todavía",
                style = MaterialTheme.typography.titleMedium,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Suma inicios repetidos de call centers (ej. 93762 o +56 2 2914) o comodines con *. Los prefijos 809 y 600 se gestionan en Protección.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            customBlockRules.forEachIndexed { index, rule ->
              CustomRuleTile(
                rule = rule,
                shape = googleSegmentedShape(index, customBlockRules.size),
                onToggle = { onToggleRule(rule.id, it) },
                onEdit = { editingRuleId = rule.id },
                onDelete = { onDeleteRule(rule.id) },
              )
            }
          }
        }
      }
    }

    // Sección 2: Excepciones / Lista blanca
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GoogleSectionHeader(title = "Lista blanca (Excepciones permitidas)")
        if (allowRules.isEmpty()) {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
          ) {
            Column(modifier = Modifier.padding(18.dp)) {
              Text(
                text = "Sin excepciones manuales",
                style = MaterialTheme.typography.titleMedium,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Útil para autorizar el 600 puntual de tu banco o clínica sin apagar el bloqueo general.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            allowRules.forEachIndexed { index, rule ->
              CustomRuleTile(
                rule = rule,
                shape = googleSegmentedShape(index, allowRules.size),
                onToggle = { onToggleRule(rule.id, it) },
                onEdit = { editingRuleId = rule.id },
                onDelete = { onDeleteRule(rule.id) },
              )
            }
          }
        }
      }
    }

    // Sección 3: Base comunitaria Chile (SpamChile) - Opción A
    item {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TonalIconBadge(
              iconRes = R.drawable.ic_globe,
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Base comunitaria SpamChile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Text(
                text = "Lista pública de números reportados en Chile",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = storeData.settings.communitySyncEnabled,
              onCheckedChange = { willEnable ->
                if (willEnable && storeData.settings.lastCommunitySyncMillis == null) {
                  showConsentDialog = true
                } else {
                  onToggleCommunitySync(willEnable)
                }
              },
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          if (storeData.settings.communitySyncEnabled) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              GooglePillChip(
                text = "${storeData.communitySpamList.size} números activos",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              if (lastSyncFormatted != null) {
                GooglePillChip(
                  text = "Actualizado: $lastSyncFormatted",
                  containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                  contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              FilledTonalButton(
                onClick = onSyncCommunity,
                enabled = !isSyncingCommunity,
                shape = CircleShape,
                modifier = Modifier.weight(1.2f),
              ) {
                if (isSyncingCommunity) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Descargando...")
                } else {
                  Icon(
                    painter = painterResource(R.drawable.ic_sync),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Actualizar lista")
                }
              }

              OutlinedButton(
                onClick = { showConsentDialog = true },
                shape = CircleShape,
                modifier = Modifier.weight(0.8f),
              ) {
                Icon(
                  painter = painterResource(R.drawable.ic_info),
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Detalles")
              }
            }
          } else {
            Text(
              text = "Desactivado por defecto. Antillamadas opera 100% desconectada sin acceder a la red.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
              onClick = { showConsentDialog = true },
              contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
              Text("Conocer más sobre la lista comunitaria y privacidad")
            }
          }
        }
      }
    }

    // Sección 4: Respaldo 100% local (Exportar / Importar)
    item {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            TonalIconBadge(
              iconRes = R.drawable.ic_backup,
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Respaldo offline (.json)",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "Guarda o restaura tus reglas sin nube y sin exponer tu historial.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Spacer(modifier = Modifier.height(14.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            FilledTonalButton(
              onClick = onExportRules,
              shape = CircleShape,
              modifier = Modifier.weight(1f),
            ) {
              Text("Exportar")
            }
            OutlinedButton(
              onClick = onImportRules,
              shape = CircleShape,
              modifier = Modifier.weight(1f),
            ) {
              Text("Importar")
            }
          }
        }
      }
    }
  }

  if (showConsentDialog) {
    CommunityPrivacyConsentDialog(
      isEnabled = storeData.settings.communitySyncEnabled,
      onDismiss = { showConsentDialog = false },
      onConfirmEnable = {
        onToggleCommunitySync(true)
        showConsentDialog = false
        onSyncCommunity()
      },
      onDisable = {
        onToggleCommunitySync(false)
        showConsentDialog = false
      },
    )
  }

  if (showCreateDialog) {
    RuleEditorDialog(
      initialRule = null,
      onDismiss = { showCreateDialog = false },
      onConfirm = { label, pattern, matchType, kind ->
        val created = onAddRule(label, pattern, matchType, kind)
        if (created) {
          showCreateDialog = false
        }
      },
    )
  }

  if (ruleBeingEdited != null) {
    RuleEditorDialog(
      initialRule = ruleBeingEdited,
      onDismiss = { editingRuleId = null },
      onConfirm = { label, pattern, matchType, kind ->
        val updated = onEditRule(ruleBeingEdited.id, label, pattern, matchType, kind)
        if (updated) {
          editingRuleId = null
        }
      },
    )
  }
}

@Composable
private fun CommunityPrivacyConsentDialog(
  isEnabled: Boolean,
  onDismiss: () -> Unit,
  onConfirmEnable: () -> Unit,
  onDisable: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(28.dp),
    icon = {
      Icon(
        painter = painterResource(R.drawable.ic_globe),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(32.dp),
      )
    },
    title = {
      Text(
        text = "Base Comunitaria y Privacidad",
        style = MaterialTheme.typography.headlineSmall,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          text = "Antillamadas permite conectarse opcionalmente al repositorio público de la comunidad chilena (SpamChile).",
          style = MaterialTheme.typography.bodyMedium,
        )

        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "🛡️ Qué hace:",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Descarga una lista de ~700 números de call centers y denuncias de estafas en Chile para filtrarlos en tu teléfono.",
              style = MaterialTheme.typography.bodySmall,
            )

            Text(
              text = "🚫 Qué NUNCA hace:",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Jamás sube ni envía tus contactos, tus llamadas ni tus números guardados. Tu agenda permanece 100% en tu celular.",
              style = MaterialTheme.typography.bodySmall,
            )

            Text(
              text = "🌐 Huella de red:",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Al actualizar, el servidor de GitHub solo ve tu IP pública al entregar el archivo, igual que abrir una página web.",
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    },
    confirmButton = {
      if (!isEnabled) {
        Button(
          onClick = onConfirmEnable,
          shape = CircleShape,
        ) {
          Text("Entendido y activar")
        }
      } else {
        Button(
          onClick = onDismiss,
          shape = CircleShape,
        ) {
          Text("Cerrar")
        }
      }
    },
    dismissButton = {
      if (!isEnabled) {
        TextButton(onClick = onDismiss) {
          Text("Mantener desconectado")
        }
      } else {
        TextButton(onClick = onDisable) {
          Text("Desactivar base comunitaria")
        }
      }
    },
  )
}

@Composable
private fun CustomRuleTile(
  rule: FilterRule,
  shape: Shape,
  onToggle: (Boolean) -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val modeText = when (rule.matchType) {
    MatchType.PREFIX -> "Empieza con: ${rule.pattern}"
    MatchType.WILDCARD -> "Comodín: ${rule.pattern}"
    MatchType.EXACT -> "Exacto: ${rule.pattern}"
  }

  val isAllow = rule.kind == RuleKind.ALLOW
  val badgeContainer = if (isAllow) {
    MaterialTheme.colorScheme.tertiaryContainer
  } else {
    MaterialTheme.colorScheme.errorContainer
  }
  val badgeContent = if (isAllow) {
    MaterialTheme.colorScheme.onTertiaryContainer
  } else {
    MaterialTheme.colorScheme.onErrorContainer
  }

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .clickable(onClick = onEdit),
    shape = shape,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TonalIconBadge(
        iconRes = if (isAllow) R.drawable.ic_shield_check else R.drawable.ic_phone_blocked,
        containerColor = badgeContainer,
        contentColor = badgeContent,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = rule.label,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text = modeText,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(
        checked = rule.enabled,
        onCheckedChange = onToggle,
      )
      IconButton(onClick = onEdit) {
        Icon(
          painter = painterResource(R.drawable.ic_edit),
          contentDescription = "Editar regla ${rule.label}",
          tint = MaterialTheme.colorScheme.primary,
        )
      }
      IconButton(onClick = onDelete) {
        Icon(
          painter = painterResource(R.drawable.ic_delete),
          contentDescription = "Eliminar regla ${rule.label}",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun RuleEditorDialog(
  initialRule: FilterRule?,
  onDismiss: () -> Unit,
  onConfirm: (label: String, pattern: String, matchType: MatchType, kind: RuleKind) -> Unit,
) {
  val stateKey = initialRule?.id ?: "new_rule"
  var selectedKind by rememberSaveable(stateKey) {
    mutableStateOf(initialRule?.kind ?: RuleKind.BLOCK)
  }
  var selectedMatchType by rememberSaveable(stateKey) {
    mutableStateOf(initialRule?.matchType ?: MatchType.PREFIX)
  }
  var pattern by rememberSaveable(stateKey) {
    mutableStateOf(initialRule?.pattern.orEmpty())
  }
  var label by rememberSaveable(stateKey) {
    mutableStateOf(initialRule?.label.orEmpty())
  }

  val isEditing = initialRule != null

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(28.dp),
    title = { Text(if (isEditing) "Editar regla" else "Nueva regla") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "¿Qué deseas hacer con este patrón?",
          style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = selectedKind == RuleKind.BLOCK,
            onClick = { selectedKind = RuleKind.BLOCK },
            label = { Text("Bloquear spam") },
            shape = CircleShape,
          )
          FilterChip(
            selected = selectedKind == RuleKind.ALLOW,
            onClick = {
              selectedKind = RuleKind.ALLOW
              if (!isEditing) {
                selectedMatchType = MatchType.EXACT
              }
            },
            label = { Text("Excepción") },
            shape = CircleShape,
          )
        }

        Text(
          text = "Modo de comparación",
          style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(
            selected = selectedMatchType == MatchType.PREFIX,
            onClick = { selectedMatchType = MatchType.PREFIX },
            label = { Text("Prefijo") },
            shape = CircleShape,
          )
          FilterChip(
            selected = selectedMatchType == MatchType.WILDCARD,
            onClick = { selectedMatchType = MatchType.WILDCARD },
            label = { Text("Comodín *") },
            shape = CircleShape,
          )
          FilterChip(
            selected = selectedMatchType == MatchType.EXACT,
            onClick = { selectedMatchType = MatchType.EXACT },
            label = { Text("Exacto") },
            shape = CircleShape,
          )
        }

        OutlinedTextField(
          value = pattern,
          onValueChange = { pattern = it.take(LocalSpamRepository.MAX_RAW_PATTERN_LENGTH) },
          label = { Text("Número o patrón") },
          placeholder = {
            Text(
              when (selectedMatchType) {
                MatchType.PREFIX -> "Ej: 93762 o +56 2 2914"
                MatchType.WILDCARD -> "Ej: +56 9 3762*"
                MatchType.EXACT -> "Ej: 600 320 3000"
              }
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = label,
          onValueChange = { label = it.take(LocalSpamRepository.MAX_LABEL_LENGTH) },
          label = { Text("Nombre descriptivo (opcional)") },
          placeholder = {
            Text(
              if (selectedKind == RuleKind.BLOCK) "Ej: Call center cobranza" else "Ej: Mi Banco"
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(label, pattern, selectedMatchType, selectedKind) },
        enabled = CallFilterEngine.isValidRulePattern(pattern),
        shape = CircleShape,
      ) {
        Text(if (isEditing) "Guardar cambios" else "Guardar")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancelar")
      }
    },
  )
}
