package cl.antillamadas.app.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import cl.antillamadas.app.data.SpamStoreData
import cl.antillamadas.app.ui.theme.AntillamadasTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      AntillamadasTheme {
        AntillamadasContent(
          storeData = SpamStoreData(),
          isRoleHeld = true,
          hasContactsPermission = true,
        )
      }
    }
  }

  @Test
  fun muestraInterruptoresDePrefijosChilenos() {
    composeTestRule.onNodeWithText("Bloquear prefijo 809").assertExists()
    composeTestRule.onNodeWithText("Bloquear prefijo 600").assertExists()
  }
}
