package cl.antillamadas.app.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.antillamadas.app.R
import cl.antillamadas.app.data.SpamStoreData
import cl.antillamadas.app.domain.DailyBlockPoint
import cl.antillamadas.app.domain.InsistentNumberItem
import cl.antillamadas.app.domain.PrefixSuggestion
import cl.antillamadas.app.domain.RuleBreakdownItem
import cl.antillamadas.app.domain.SpamStatsCalculator
import cl.antillamadas.app.domain.StatsPeriod
import cl.antillamadas.app.domain.TimeSlotStat
import cl.antillamadas.app.ui.components.GooglePillChip
import cl.antillamadas.app.ui.components.GoogleSectionHeader
import cl.antillamadas.app.ui.components.TonalIconBadge
import cl.antillamadas.app.ui.components.googleSegmentedShape
import kotlin.math.roundToInt

@Composable
fun StatsTab(
  storeData: SpamStoreData,
  onBlockSuggestedPrefix: (prefix5: String) -> Unit,
  onDismissSuggestion: (prefix5: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedPeriod by rememberSaveable { mutableStateOf(StatsPeriod.DAYS_30) }

  val summary = remember(storeData, selectedPeriod) {
    SpamStatsCalculator.compute(
      blockedHistory = storeData.blockedHistory,
      rules = storeData.rules,
      settings = storeData.settings,
      observedPrefixHits = storeData.observedPrefixHits,
      dismissedPrefixes = storeData.dismissedSuggestedPrefixes,
      period = selectedPeriod,
    )
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // 1. Selector rápido de período estilo Google Wellbeing
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        StatsPeriod.entries.forEach { period ->
          FilterChip(
            selected = selectedPeriod == period,
            onClick = { selectedPeriod = period },
            label = { Text(period.label) },
            shape = CircleShape,
          )
        }
      }
    }

    // 2. Sugerencias inteligentes de patrones repetidos (si existen)
    if (summary.smartSuggestions.isNotEmpty()) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          GoogleSectionHeader(title = "Sugerencia inteligente")
          summary.smartSuggestions.forEach { suggestion ->
            SmartPrefixSuggestionCard(
              suggestion = suggestion,
              onBlock = { onBlockSuggestedPrefix(suggestion.prefix5) },
              onDismiss = { onDismissSuggestion(suggestion.prefix5) },
            )
          }
        }
      }
    }

    // 3. Tarjetas Hero: Interrupciones evitadas y Tiempo ahorrado
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        HeroMetricCard(
          title = "Llamadas cortadas",
          bigValue = summary.totalBlocked.toString(),
          subtitle = if (summary.uniqueNumbersBlocked == 1) {
            "1 número distinto"
          } else {
            "${summary.uniqueNumbersBlocked} números distintos"
          },
          accent = true,
          modifier = Modifier.weight(1f),
        )
        HeroMetricCard(
          title = "Tiempo ahorrado",
          bigValue = SpamStatsCalculator.formatSavedTime(summary.estimatedSecondsSaved),
          subtitle = "sin interrupciones",
          accent = false,
          modifier = Modifier.weight(1f),
        )
      }
    }

    // 4. Gráfico de barras de los últimos 7 días
    item {
      SevenDayActivityCard(points = summary.dailyLast7Days)
    }

    // 5. Horarios punta de spam (Mañana, Tarde, Noche)
    item {
      PeakHoursCard(
        timeSlots = summary.timeSlots,
        peakHourRange = summary.peakHourRange,
      )
    }

    // 6. Desglose por Prefijo / Regla
    item {
      RuleBreakdownCard(items = summary.ruleBreakdown)
    }

    // 7. Ranking de números exactos más insistentes
    item {
      TopInsistentNumbersSection(items = summary.topInsistentNumbers)
    }
  }
}

@Composable
private fun SmartPrefixSuggestionCard(
  suggestion: PrefixSuggestion,
  onBlock: () -> Unit,
  onDismiss: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.secondaryContainer,
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        TonalIconBadge(
          iconRes = R.drawable.ic_lightbulb,
          containerColor = MaterialTheme.colorScheme.secondary,
          contentColor = MaterialTheme.colorScheme.onSecondary,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Posible call center: ${suggestion.displayPrefix}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
          Text(
            text = "${suggestion.distinctNumbersCount} números desconocidos distintos con este mismo inicio (${suggestion.totalCallsCount} llamadas recientes).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
          )
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onDismiss) {
          Text("Ignorar")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = onBlock,
          shape = CircleShape,
        ) {
          Text("Bloquear prefijo")
        }
      }
    }
  }
}

@Composable
private fun HeroMetricCard(
  title: String,
  bigValue: String,
  subtitle: String,
  accent: Boolean,
  modifier: Modifier = Modifier,
) {
  val container = if (accent) {
    MaterialTheme.colorScheme.primaryContainer
  } else {
    MaterialTheme.colorScheme.surfaceContainerLow
  }
  val titleColor = if (accent) {
    MaterialTheme.colorScheme.onPrimaryContainer
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant
  }
  val valueColor = if (accent) {
    MaterialTheme.colorScheme.onPrimaryContainer
  } else {
    MaterialTheme.colorScheme.onSurface
  }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(24.dp),
    color = container,
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = titleColor,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = bigValue,
        style = MaterialTheme.typography.displaySmall,
        color = valueColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = titleColor.copy(alpha = 0.85f),
      )
    }
  }
}

@Composable
private fun SevenDayActivityCard(points: List<DailyBlockPoint>) {
  val maxCount = (points.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
  val totalWeek = points.sumOf { it.count }
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
  val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "Actividad de los últimos 7 días",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = if (totalWeek == 1) "1 bloqueo esta semana" else "$totalWeek bloqueos esta semana",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        GooglePillChip(
          text = "7 días",
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(148.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        points.forEach { point ->
          val ratio = if (point.count == 0) 0f else (point.count.toFloat() / maxCount).coerceIn(0.12f, 1f)
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
          ) {
            Text(
              text = if (point.count > 0) point.count.toString() else "",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = if (point.isToday) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Canvas(
              modifier = Modifier
                .width(22.dp)
                .weight(1f),
            ) {
              val radius = CornerRadius(size.width / 2f, size.width / 2f)
              drawRoundRect(
                color = trackColor,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = radius,
              )
              if (ratio > 0f) {
                val barHeight = (size.height * ratio).coerceAtLeast(size.width)
                drawRoundRect(
                  color = if (point.isToday) primaryColor else secondaryBarColor,
                  topLeft = Offset(0f, size.height - barHeight),
                  size = Size(size.width, barHeight),
                  cornerRadius = radius,
                )
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = point.dayShortLabel,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (point.isToday) FontWeight.Bold else FontWeight.Normal,
              color = if (point.isToday) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PeakHoursCard(
  timeSlots: List<TimeSlotStat>,
  peakHourRange: String?,
) {
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
          iconRes = R.drawable.ic_schedule,
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          badgeSize = 36.dp,
          iconSize = 18.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Horarios punta de spam",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = if (peakHourRange != null) {
              "Hora más frecuente: $peakHourRange"
            } else {
              "Se calculará cuando se bloqueen llamadas"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        timeSlots.forEach { slot ->
          val pct = (slot.fraction * 100).roundToInt()
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "${slot.label} (${slot.hoursLabel})",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
              )
              Text(
                text = "${slot.count} · $pct%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Spacer(modifier = Modifier.height(5.dp))
            ProportionalBar(
              fraction = slot.fraction,
              barColor = MaterialTheme.colorScheme.secondary,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RuleBreakdownCard(items: List<RuleBreakdownItem>) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Text(
        text = "Bloqueos por prefijo o regla",
        style = MaterialTheme.typography.titleMedium,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Qué filtros te están protegiendo más en este período.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(14.dp))

      if (items.isEmpty()) {
        Text(
          text = "Aún no hay llamadas bloqueadas en este período.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          items.forEach { item ->
            val pct = (item.fraction * 100).roundToInt()
            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = item.label,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium,
                  modifier = Modifier.weight(1f).padding(end = 8.dp),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${item.count} ($pct%)",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              ProportionalBar(
                fraction = item.fraction,
                barColor = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TopInsistentNumbersSection(items: List<InsistentNumberItem>) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    GoogleSectionHeader(title = "Números más insistentes")

    if (items.isEmpty()) {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
      ) {
        Text(
          text = "Aquí aparecerá el ranking de los números exactos que más veces intenten llamarte.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(18.dp),
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, item ->
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = googleSegmentedShape(index, items.size),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "#${index + 1}",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                  text = item.formattedNumber,
                  style = MaterialTheme.typography.bodyLarge,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = item.lastReason,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              GooglePillChip(
                text = if (item.count == 1) "1 bloqueo" else "${item.count} bloqueos",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ProportionalBar(
  fraction: Float,
  barColor: androidx.compose.ui.graphics.Color,
) {
  val safeFraction = fraction.coerceIn(0f, 1f)
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(8.dp)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
  ) {
    if (safeFraction > 0f) {
      Box(
        modifier = Modifier
          .fillMaxWidth(safeFraction.coerceAtLeast(0.04f))
          .fillMaxHeight()
          .clip(CircleShape)
          .background(barColor),
      )
    }
  }
}
