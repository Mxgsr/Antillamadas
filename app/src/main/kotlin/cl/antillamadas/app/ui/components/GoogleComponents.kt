package cl.antillamadas.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Forma segmentada estilo Google Pixel / Android Settings (Material 3 Expressive):
 * esquinas exteriores amplias (24.dp) y esquinas interiores suaves (6.dp) separadas por 2.dp.
 */
fun googleSegmentedShape(index: Int, totalCount: Int): Shape {
  val outer = 24.dp
  val inner = 6.dp
  return when {
    totalCount <= 1 -> RoundedCornerShape(outer)
    index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
    index == totalCount - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
    else -> RoundedCornerShape(inner)
  }
}

@Composable
fun TonalIconBadge(
  @DrawableRes iconRes: Int,
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
  contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
  badgeSize: Dp = 40.dp,
  iconSize: Dp = 20.dp,
) {
  Box(
    modifier = modifier
      .size(badgeSize)
      .clip(CircleShape)
      .background(containerColor),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      painter = painterResource(iconRes),
      contentDescription = null,
      tint = contentColor,
      modifier = Modifier.size(iconSize),
    )
  }
}

@Composable
fun GoogleSectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.primary,
    )
    if (trailing != null) {
      trailing()
    }
  }
}

@Composable
fun GoogleSwitchTile(
  @DrawableRes iconRes: Int,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  shape: Shape,
  modifier: Modifier = Modifier,
  iconContainerColor: Color = if (checked) {
    MaterialTheme.colorScheme.primaryContainer
  } else {
    MaterialTheme.colorScheme.surfaceContainerHigh
  },
  iconContentColor: Color = if (checked) {
    MaterialTheme.colorScheme.onPrimaryContainer
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant
  },
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .clip(shape)
      .clickable { onCheckedChange(!checked) },
    shape = shape,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation = 0.dp,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TonalIconBadge(
        iconRes = iconRes,
        containerColor = iconContainerColor,
        contentColor = iconContentColor,
      )
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
      )
    }
  }
}

@Composable
fun GooglePillChip(
  text: String,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
  contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = CircleShape,
    color = containerColor,
    contentColor = contentColor,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}
