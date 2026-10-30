package cl.antillamadas.app.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import cl.antillamadas.app.data.LocalSpamRepository
import cl.antillamadas.app.domain.CallFilterEngine
import cl.antillamadas.app.domain.FilterDecision

/**
 * El "Guardián" de Antillamadas.
 *
 * Android despierta automáticamente este servicio durante unos milisegundos cada vez que
 * entra una llamada telefónica, incluso si la aplicación estaba completamente cerrada.
 */
class AntillamadasScreeningService : CallScreeningService() {

  override fun onScreenCall(callDetails: Call.Details) {
    try {
      // Nunca filtramos llamadas salientes hechas por ti mismo.
      if (callDetails.callDirection == Call.Details.DIRECTION_OUTGOING) {
        allowCall(callDetails)
        return
      }

      val rawNumber = callDetails.handle?.schemeSpecificPart
      val repository = LocalSpamRepository.getInstance(applicationContext)
      val snapshot = repository.snapshot()

      val isSavedContact = if (snapshot.settings.allowContacts) {
        ContactsLookup.isNumberSavedInContacts(applicationContext, rawNumber)
      } else {
        false
      }

      val decision = CallFilterEngine.evaluate(
        rawNumber = rawNumber,
        rules = snapshot.rules,
        settings = snapshot.settings,
        isSavedContact = isSavedContact,
        recentBlockedHistory = snapshot.blockedHistory,
        communitySpamList = snapshot.communitySpamList,
      )

      when (decision) {
        is FilterDecision.Allow -> {
          if (!isSavedContact) {
            runCatching { repository.recordObservedUnknownCall(rawNumber) }
          }
          allowCall(callDetails)
        }

        is FilterDecision.Block -> {
          // Aunque el disco estuviera lleno, igual rechazamos la llamada spam sin caernos.
          runCatching {
            repository.recordBlockedCall(
              rawNumber = rawNumber,
              reason = decision.reason,
              matchedRuleId = decision.matchedRuleId,
            )
          }
          rejectSilently(callDetails)
        }
      }
    } catch (_: Throwable) {
      // Política defensiva fail-open: si ocurre un fallo inesperado (por ejemplo, teléfono recién
      // reiniciado en modo Direct Boot antes del primer PIN), permitimos sonar la llamada para
      // jamás perder una llamada legítima ni bloquear el servicio telefónico de Android.
      runCatching { allowCall(callDetails) }
    }
  }

  private fun allowCall(callDetails: Call.Details) {
    val response = CallResponse.Builder()
      .setDisallowCall(false)
      .setRejectCall(false)
      .setSkipCallLog(false)
      .setSkipNotification(false)
      .build()
    respondToCall(callDetails, response)
  }

  /**
   * Corta y rechaza la llamada automáticamente antes de que suene el timbre,
   * evitando además notificaciones molestas de llamada perdida.
   */
  private fun rejectSilently(callDetails: Call.Details) {
    val response = CallResponse.Builder()
      .setDisallowCall(true)
      .setRejectCall(true)
      .setSkipCallLog(false)
      .setSkipNotification(true)
      .build()
    respondToCall(callDetails, response)
  }
}
