package cl.antillamadas.app.screening

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Consulta 100% local a la agenda del teléfono.
 * Solo revisa si el número entrante existe en tus Contactos para no cortarlo por error.
 * Jamás copia, sube ni comparte tu agenda.
 */
object ContactsLookup {

  fun hasContactsPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun isNumberSavedInContacts(context: Context, rawNumber: String?): Boolean {
    val number = rawNumber?.trim().orEmpty()
    if (number.isEmpty() || !hasContactsPermission(context)) {
      return false
    }

    return runCatching {
      val lookupUri = Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        Uri.encode(number),
      )
      val projection = arrayOf(ContactsContract.PhoneLookup._ID)
      context.contentResolver.query(lookupUri, projection, null, null, null)?.use { cursor ->
        cursor.moveToFirst()
      } ?: false
    }.getOrDefault(false)
  }
}
