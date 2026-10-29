package cl.antillamadas.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.antillamadas.app.R
import cl.antillamadas.app.data.SpamStoreData
import cl.antillamadas.app.domain.BuiltInChileRules
import cl.antillamadas.app.domain.CallFilterEngine
import cl.antillamadas.app.domain.FilterDecision
import cl.antillamadas.app.domain.RuleKind
import cl.antillamadas.app.ui.components.GooglePillChip
import cl.antillamadas.app.ui.components.GoogleSectionHeader
import cl.antillamadas.app.ui.components.GoogleSwitchTile
import cl.antillamadas.app.ui.components.googleSegmentedShape

@Composable
fun ProtectionTab(
  storeData: SpamStoreData,
  isRoleHeld: Boolean,
  hasContactsPermission: Boolean,
  onRequestScreeningRole: () -> Unit,
  onRequestContactsPermission: () -> Unit,
  onToggleProtectionEnabled: (Boolean) -> Unit,
  onRequestAddQuickTile: () -> Unit,
  onToggleRule: (String, Boolean) -> Unit,
  onToggleAllowContacts: (Boolean) -> Unit,
  onToggleAllowRepeatedCalls: (Boolean) -> Unit,
  onToggleBlockHidden: (Boolean) -> Unit,
  onToggleBlockInternational: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val rule809 = storeData.rules.find { it.id == BuiltInChileRules.ID_PREFIX_809 }
  val rule600 = storeData.rules.find { it.id == BuiltInChileRules.ID_PREFIX_600 }
  val activeBlockCount = storeData.rules.count { it.enabled && it.kind == RuleKind.BLOCK }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    // 1. Tarjeta Hero estilo Google Personal Safety (con pausa rápida y acceso a cortina de Android)
    item {
      PixelSecurityStatusHero(
        isRoleHeld = isRoleHeld,
        protectionEnabled = storeData.settings.protectionEnabled,
        activeRulesCount = activeBlockCount,
        blockedCallsCount = storeData.blockedHistory.size,
        onRequestScreeningRole = onRequestScreeningRole,
        onToggleProtectionEnabled = onToggleProtectionEnabled,
        onRequestAddQuickTile = onRequestAddQuickTile,
      )
    }

    // 2. Grupo segmentado: Prefijos oficiales de Chile (Subtel)
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GoogleSectionHeader(title = "Prefijos masivos en Chile (Subtel)")
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          if (rule809 != null) {
            GoogleSwitchTile(
              iconRes = R.drawable.ic_phone_blocked,
              title = "Bloquear prefijo 809",
              subtitle = "Telemarketing, promociones y ventas no solicitadas",
              checked = rule809.enabled,
              onCheckedChange = { onToggleRule(rule809.id, it) },
              shape = googleSegmentedShape(0, if (rule600 != null) 2 else 1),
            )
          }
          if (rule600 != null) {
            GoogleSwitchTile(
              iconRes = R.drawable.ic_phone_blocked,
              title = "Bloquear prefijo 600",
              subtitle = "Servicios masivos de empresas y cobranzas",
              checked = rule600.enabled,
              onCheckedChange = { onToggleRule(rule600.id, it) },
              shape = googleSegmentedShape(if (rule809 != null) 1 else 0, if (rule809 != null) 2 else 1),
            )
          }
        }
      }
    }

    // 3. Grupo segmentado: Protecciones inteligentes adicionales
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GoogleSectionHeader(title = "Protecciones adicionales")
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          GoogleSwitchTile(
            iconRes = R.drawable.ic_contacts,
            title = "Permitir siempre mis Contactos",
            subtitle = "Jamás corta números guardados en tu agenda",
            checked = storeData.settings.allowContacts,
            onCheckedChange = { enabled ->
              onToggleAllowContacts(enabled)
              if (enabled && !hasContactsPermission) {
                onRequestContactsPermission()
              }
            },
            shape = googleSegmentedShape(0, 4),
          )

          GoogleSwitchTile(
            iconRes = R.drawable.ic_repeat_call,
            title = "Dejar pasar al 2.º intento",
            subtitle = "Permite sonar si el mismo número insiste en menos de 3 min (salvo 809)",
            checked = storeData.settings.allowRepeatedCalls,
            onCheckedChange = onToggleAllowRepeatedCalls,
            shape = googleSegmentedShape(1, 4),
          )

          GoogleSwitchTile(
            iconRes = R.drawable.ic_visibility_off,
            title = "Bloquear números ocultos o privados",
            subtitle = "Rechaza llamadas que esconden su número",
            checked = storeData.settings.blockHiddenNumbers,
            onCheckedChange = onToggleBlockHidden,
            shape = googleSegmentedShape(2, 4),
          )

          GoogleSwitchTile(
            iconRes = R.drawable.ic_globe,
            title = "Bloquear llamadas internacionales",
            subtitle = "Rechaza prefijos extranjeros fuera de +56",
            checked = storeData.settings.blockInternationalNumbers,
            onCheckedChange = onToggleBlockInternational,
            shape = googleSegmentedShape(3, 4),
          )
        }

        if (storeData.settings.allowContacts && !hasContactsPermission) {
          FilledTonalButton(
            onClick = onRequestContactsPermission,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Autorizar lectura local de Contactos")
          }
        }
      }
    }

    // 4. Simulador instantáneo estilo barra cápsula de Google
    item {
      LiveCallSimulatorCard(storeData = storeData)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PixelSecurityStatusHero(
  isRoleHeld: Boolean,
  protectionEnabled: Boolean,
  activeRulesCount: Int,
  blockedCallsCount: Int,
  onRequestScreeningRole: () -> Unit,
  onToggleProtectionEnabled: (Boolean) -> Unit,
  onRequestAddQuickTile: () -> Unit,
) {
  val containerColor = when {
    !isRoleHeld -> MaterialTheme.colorScheme.errorContainer
    protectionEnabled -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.secondaryContainer
  }
  val contentColor = when {
    !isRoleHeld -> MaterialTheme.colorScheme.onErrorContainer
    protectionEnabled -> MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.onSecondaryContainer
  }
  val badgeCircleColor = when {
    !isRoleHeld -> MaterialTheme.colorScheme.error
    protectionEnabled -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.secondary
  }
  val badgeIconColor = when {
    !isRoleHeld -> MaterialTheme.colorScheme.onError
    protectionEnabled -> MaterialTheme.colorScheme.onPrimary
    else -> MaterialTheme.colorScheme.onSecondary
  }
  val heroIconRes = when {
    !isRoleHeld -> R.drawable.ic_shield_alert
    protectionEnabled -> R.drawable.ic_shield_check
    else -> R.drawable.ic_pause_circle
  }
  val heroTitle = when {
    !isRoleHeld -> "Protección desactivada"
    protectionEnabled -> "Teléfono protegido"
    else -> "Protección en pausa"
  }
  val heroSubtitle = when {
    !isRoleHeld -> "Elige a Antillamadas como filtro predeterminado de spam"
    protectionEnabled -> "Las llamadas spam se cortan antes de sonar"
    else -> "Pausa temporal activa (ej. esperando repartidor o código)"
  }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(28.dp),
    color = containerColor,
    contentColor = contentColor,
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(badgeCircleColor),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            painter = painterResource(heroIconRes),
            contentDescription = null,
            tint = badgeIconColor,
            modifier = Modifier.size(28.dp),
          )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = heroTitle,
            style = MaterialTheme.typography.headlineSmall,
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = heroSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor.copy(alpha = 0.85f),
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      when {
        !isRoleHeld -> {
          Button(
            onClick = onRequestScreeningRole,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Activar bloqueo automático")
          }
        }

        !protectionEnabled -> {
          Button(
            onClick = { onToggleProtectionEnabled(true) },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Reanudar protección ahora")
          }
        }

        else -> {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            GooglePillChip(
              text = "$activeRulesCount reglas activas",
              containerColor = contentColor.copy(alpha = 0.12f),
              contentColor = contentColor,
            )
            GooglePillChip(
              text = "$blockedCallsCount llamadas cortadas",
              containerColor = contentColor.copy(alpha = 0.12f),
              contentColor = contentColor,
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = { onToggleProtectionEnabled(false) },
              shape = CircleShape,
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
              Icon(
                painter = painterResource(R.drawable.ic_pause_circle),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Pausar", style = MaterialTheme.typography.labelLarge)
            }

            FilledTonalButton(
              onClick = onRequestAddQuickTile,
              shape = CircleShape,
              colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.primary,
              ),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                painter = painterResource(R.drawable.ic_qs_tile),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Botón en cortina", style = MaterialTheme.typography.labelLarge)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LiveCallSimulatorCard(storeData: SpamStoreData) {
  var testNumber by rememberSaveable { mutableStateOf("") }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Text(
        text = "Simulador instantáneo",
        style = MaterialTheme.typography.titleMedium,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = "Comprueba cómo responderá tu filtro ante cualquier número.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        value = testNumber,
        onValueChange = { testNumber = it },
        placeholder = { Text("Ej: +56 809 123 456") },
        leadingIcon = {
          Icon(
            painter = painterResource(R.drawable.ic_dialpad),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
          focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
      )

      if (testNumber.isNotBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        val decision = CallFilterEngine.evaluate(
          rawNumber = testNumber,
          rules = storeData.rules,
          settings = storeData.settings,
          isSavedContact = false,
          recentBlockedHistory = storeData.blockedHistory,
        )
        val isBlocked = decision is FilterDecision.Block
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (isBlocked) {
            MaterialTheme.colorScheme.errorContainer
          } else {
            MaterialTheme.colorScheme.tertiaryContainer
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          val message = when (decision) {
            is FilterDecision.Block -> "Se cortará sin sonar · ${decision.reason}"
            is FilterDecision.Allow -> "Se permitirá sonar · ${decision.reason}"
          }
          Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isBlocked) {
              MaterialTheme.colorScheme.onErrorContainer
            } else {
              MaterialTheme.colorScheme.onTertiaryContainer
            },
          )
        }
      }
    }
  }
}
