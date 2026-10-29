package cl.antillamadas.app.screening

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

/**
 * En Android 10+, ninguna app puede interceptar llamadas en secreto:
 * el usuario debe autorizar explícitamente a la app con el rol `ROLE_CALL_SCREENING`
 * ("Identificador de llamadas y spam").
 */
object CallRoleHelper {

  fun isCallScreeningRoleHeld(context: Context): Boolean {
    val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
    return roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
      roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
  }

  fun createRequestRoleIntent(context: Context): Intent? {
    val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
    if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
    return roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
  }
}
