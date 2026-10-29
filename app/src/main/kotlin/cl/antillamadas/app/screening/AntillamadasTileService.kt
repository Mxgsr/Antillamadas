package cl.antillamadas.app.screening

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import cl.antillamadas.app.MainActivity
import cl.antillamadas.app.R
import cl.antillamadas.app.data.LocalSpamRepository

enum class AddTileOutcome {
  ADDED,
  ALREADY_ADDED,
  DISMISSED,
  MANUAL_INSTRUCTIONS_NEEDED,
}

/**
 * Botón rápido en la cortina superior de Android (Quick Settings Tile).
 *
 * Permite ver de un vistazo si Antillamadas está activo y pausar o reanudar el bloqueo
 * con 1 toque (por ejemplo, si estás esperando la llamada de un repartidor o código telefónico)
 * sin tener que abrir la aplicación.
 */
class AntillamadasTileService : TileService() {

  override fun onStartListening() {
    super.onStartListening()
    syncTile()
  }

  override fun onClick() {
    super.onClick()
    try {
      val appContext = applicationContext
      if (!CallRoleHelper.isCallScreeningRoleHeld(appContext)) {
        openAppAndCollapse()
        return
      }

      val repository = LocalSpamRepository.getInstance(appContext)
      val nextEnabled = !repository.snapshot().settings.protectionEnabled
      repository.updateSettings { it.copy(protectionEnabled = nextEnabled) }
      syncTile()
    } catch (_: Throwable) {
      // Protección defensiva (ej. pantalla bloqueada recién encendido antes del primer PIN).
      runCatching { syncTile() }
    }
  }

  private fun syncTile() {
    val tile = qsTile ?: return
    runCatching {
      val appContext = applicationContext
      val roleHeld = CallRoleHelper.isCallScreeningRoleHeld(appContext)
      val protectionEnabled = LocalSpamRepository.getInstance(appContext)
        .snapshot()
        .settings
        .protectionEnabled

      tile.label = getString(R.string.app_name)
      when {
        !roleHeld -> {
          tile.state = Tile.STATE_INACTIVE
          tile.subtitle = "Falta activar"
          tile.icon = Icon.createWithResource(this, R.drawable.ic_shield_alert)
        }
        protectionEnabled -> {
          tile.state = Tile.STATE_ACTIVE
          tile.subtitle = "Protegiendo"
          tile.icon = Icon.createWithResource(this, R.drawable.ic_shield_check)
        }
        else -> {
          tile.state = Tile.STATE_INACTIVE
          tile.subtitle = "En pausa"
          tile.icon = Icon.createWithResource(this, R.drawable.ic_pause_circle)
        }
      }
      tile.updateTile()
    }
  }

  private fun openAppAndCollapse() {
    val launchIntent = Intent(this, MainActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val pendingIntent = PendingIntent.getActivity(
        this,
        0,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
      startActivityAndCollapse(pendingIntent)
    } else {
      @Suppress("DEPRECATION")
      startActivityAndCollapse(launchIntent)
    }
  }

  companion object {
    /**
     * Le avisa a Android que refresque inmediatamente el estado visual del botón en la cortina.
     */
    fun requestSync(context: Context) {
      runCatching {
        val appContext = context.applicationContext
        requestListeningState(
          appContext,
          ComponentName(appContext, AntillamadasTileService::class.java),
        )
      }
    }

    /**
     * En Android 13+ abre el diálogo nativo del sistema para fijar el botón en la cortina superior
     * con un solo toque. En Android 10–12 indica cómo agregarlo con el lápiz de edición.
     */
    fun requestAddTileToQuickSettings(
      context: Context,
      onResult: (AddTileOutcome) -> Unit,
    ) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        onResult(AddTileOutcome.MANUAL_INSTRUCTIONS_NEEDED)
        return
      }

      val statusBarManager = context.getSystemService(StatusBarManager::class.java)
      if (statusBarManager == null) {
        onResult(AddTileOutcome.MANUAL_INSTRUCTIONS_NEEDED)
        return
      }

      runCatching {
        statusBarManager.requestAddTileService(
          ComponentName(context, AntillamadasTileService::class.java),
          context.getString(R.string.app_name),
          Icon.createWithResource(context, R.drawable.ic_shield_check),
          context.mainExecutor,
        ) { resultCode ->
          val outcome = when (resultCode) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> AddTileOutcome.ADDED
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> AddTileOutcome.ALREADY_ADDED
            else -> AddTileOutcome.DISMISSED
          }
          onResult(outcome)
        }
      }.onFailure {
        onResult(AddTileOutcome.MANUAL_INSTRUCTIONS_NEEDED)
      }
    }
  }
}
