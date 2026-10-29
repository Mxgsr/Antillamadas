package cl.antillamadas.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.antillamadas.app.R
import cl.antillamadas.app.domain.BlockedCallRecord
import cl.antillamadas.app.domain.PhoneNumberNormalizer
import cl.antillamadas.app.ui.components.GooglePillChip
import cl.antillamadas.app.ui.components.GoogleSectionHeader
import cl.antillamadas.app.ui.components.TonalIconBadge
import cl.antillamadas.app.ui.components.googleSegmentedShape
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

private data class HistoryDateBucket(
  val title: String,
  val records: List<BlockedCallRecord>,
)

@Composable
fun HistoryTab(
  blockedCalls: List<BlockedCallRecord>,
  onCopyForSernac: (String) -> Unit,
  onAllowNumber: (String) -> Unit,
  onDeleteRecord: (String) -> Unit,
  onClearAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (blockedCalls.isEmpty()) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .padding(32.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
          modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            painter = painterResource(R.drawable.ic_shield_check),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "Sin llamadas bloqueadas",
          style = MaterialTheme.typography.titleLarge,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Cada vez que Antillamadas corte un número spam, verás aquí la hora, el motivo y cuántas veces insistió.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }
    return
  }

  var searchQuery by rememberSaveable { mutableStateOf("") }

  val repeatCounts = remember(blockedCalls) {
    buildMap<String, Int> {
      for (call in blockedCalls) {
        val key = PhoneNumberNormalizer.canonicalKey(call.rawNumber)
        if (key.isNotEmpty()) {
          put(key, (get(key) ?: 0) + 1)
        }
      }
    }
  }

  val filteredCalls = remember(blockedCalls, searchQuery) {
    val trimmed = searchQuery.trim()
    if (trimmed.isEmpty()) {
      blockedCalls
    } else {
      val queryLower = trimmed.lowercase(Locale.ROOT)
      val queryDigits = trimmed.filter { it.isDigit() }
      blockedCalls.filter { record ->
        val matchesText = record.formattedNumber.lowercase(Locale.ROOT).contains(queryLower) ||
          record.reason.lowercase(Locale.ROOT).contains(queryLower) ||
          record.rawNumber.lowercase(Locale.ROOT).contains(queryLower)
        val matchesDigits = queryDigits.isNotEmpty() && (
          PhoneNumberNormalizer.formatForSernac(record.rawNumber).contains(queryDigits) ||
            record.rawNumber.filter { it.isDigit() }.contains(queryDigits)
          )
        matchesText || matchesDigits
      }
    }
  }

  val buckets = remember(filteredCalls) {
    groupHistoryByDate(filteredCalls)
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Barra cápsula de búsqueda estilo Google Phone
    item {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Buscar número o motivo...") },
        leadingIcon = {
          Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        trailingIcon = if (searchQuery.isNotEmpty()) {
          {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Limpiar búsqueda",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          null
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
          unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
      )
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        val countLabel = if (searchQuery.isBlank()) {
          if (blockedCalls.size == 1) "1 llamada rechazada" else "${blockedCalls.size} llamadas rechazadas"
        } else {
          "${filteredCalls.size} de ${blockedCalls.size} coincidencias"
        }
        Text(
          text = countLabel,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClearAll) {
          Text("Limpiar todo")
        }
      }
    }

    if (filteredCalls.isEmpty()) {
      item {
        Surface(
          shape = RoundedCornerShape(24.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Ninguna llamada bloqueada coincide con \"$searchQuery\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
          )
        }
      }
    } else {
      buckets.forEach { bucket ->
        item(key = "bucket_${bucket.title}") {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GoogleSectionHeader(title = bucket.title)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              bucket.records.forEachIndexed { index, record ->
                val key = PhoneNumberNormalizer.canonicalKey(record.rawNumber)
                val totalRepeats = if (key.isNotEmpty()) (repeatCounts[key] ?: 1) else 1
                BlockedCallItemTile(
                  record = record,
                  repeatCount = totalRepeats,
                  shape = googleSegmentedShape(index, bucket.records.size),
                  onCopyForSernac = { onCopyForSernac(record.rawNumber) },
                  onAllowNumber = { onAllowNumber(record.rawNumber) },
                  onDelete = { onDeleteRecord(record.id) },
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun groupHistoryByDate(records: List<BlockedCallRecord>): List<HistoryDateBucket> {
  val zone = ZoneId.systemDefault()
  val today = LocalDate.now(zone)
  val yesterday = today.minusDays(1)

  val todayList = mutableListOf<BlockedCallRecord>()
  val yesterdayList = mutableListOf<BlockedCallRecord>()
  val olderList = mutableListOf<BlockedCallRecord>()

  for (record in records) {
    val date = Instant.ofEpochMilli(record.timestampMillis).atZone(zone).toLocalDate()
    when (date) {
      today -> todayList.add(record)
      yesterday -> yesterdayList.add(record)
      else -> olderList.add(record)
    }
  }

  return buildList {
    if (todayList.isNotEmpty()) add(HistoryDateBucket("Hoy", todayList))
    if (yesterdayList.isNotEmpty()) add(HistoryDateBucket("Ayer", yesterdayList))
    if (olderList.isNotEmpty()) add(HistoryDateBucket("Anteriores", olderList))
  }
}

@Composable
private fun BlockedCallItemTile(
  record: BlockedCallRecord,
  repeatCount: Int,
  shape: Shape,
  onCopyForSernac: () -> Unit,
  onAllowNumber: () -> Unit,
  onDelete: () -> Unit,
) {
  val formattedDate = remember(record.timestampMillis) {
    SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.forLanguageTag("es-CL"))
      .format(Date(record.timestampMillis))
  }
  val sernacDigits = remember(record.rawNumber) {
    PhoneNumberNormalizer.formatForSernac(record.rawNumber)
  }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = shape,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TonalIconBadge(
          iconRes = R.drawable.ic_phone_blocked,
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = record.formattedNumber,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            if (repeatCount > 1) {
              GooglePillChip(
                text = "×$repeatCount",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }
          Text(
            text = record.reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = formattedDate,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
          )
        }
        IconButton(onClick = onDelete) {
          Icon(
            painter = painterResource(R.drawable.ic_delete),
            contentDescription = "Borrar del historial",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (sernacDigits.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilledTonalButton(
            onClick = onCopyForSernac,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          ) {
            Icon(
              painter = painterResource(R.drawable.ic_copy),
              contentDescription = null,
              modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copiar p/ SERNAC", style = MaterialTheme.typography.labelMedium)
          }

          TextButton(
            onClick = onAllowNumber,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Text("Permitir número", style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }
  }
}
