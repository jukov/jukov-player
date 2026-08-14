package info.jukov.player

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import kotlin.test.assertEquals

class AndroidAppSmokeTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(notificationPermissionRule())
        .around(composeRule)

    @Before
    fun resetSession() {
        val graph = (composeRule.activity.application as JukovApplication).graph
        TestBackend.reset()
        runBlocking { graph.authRepository.logout() }
        graph.playbackController.stopAndClear()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Sign in").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun coldStartShowsLogin() {
        composeRule.onNodeWithText("Connect to your Subsonic server").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in").assertIsDisplayed()
        composeRule.onNodeWithTag("login.submit").assertHasClickAction()
    }

    @Test
    fun loginLibraryTracksAndPlay() {
        login()

        composeRule.onNodeWithTag("library.tracks")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithTag("track.track-1")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        val graph = (composeRule.activity.application as JukovApplication).graph
        composeRule.waitUntil(timeoutMillis = 5_000) {
            graph.playbackController.state.value.content?.currentTrack?.id == "track-1"
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Test Song").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun backAfterLoginDoesNotReturnToLogin() {
        login()
        composeRule.onNodeWithTag("library.tracks").performClick()
        composeRule.onNodeWithTag("track.track-1").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithTag("library.tracks").assertIsDisplayed()
        composeRule.onNodeWithText("Connect to your Subsonic server").assertDoesNotExist()
    }

    @Test
    fun activityRecreationKeepsAuthorizedDestination() {
        login()
        composeRule.onNodeWithTag("library.tracks").performClick()
        waitForTrackList()

        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("track.track-1").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("library.tracks").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Connect to your Subsonic server").assertDoesNotExist()
    }

    @Test
    fun logoutReplacesAuthorizedHistory() {
        login()
        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Sign out").performClick()
        composeRule.onNodeWithText("Sign out?").assertIsDisplayed()
        composeRule.onNodeWithText("Sign out").performClick()
        composeRule.onNodeWithText("Connect to your Subsonic server").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activityRule.scenario.state == Lifecycle.State.DESTROYED
        }
        assertEquals(Lifecycle.State.DESTROYED, composeRule.activityRule.scenario.state)
    }

    @Test
    fun rejectedLoginCanBeRetried() {
        TestBackend.rejectNextLogin()
        enterCredentials()
        composeRule.onNodeWithTag("login.submit").performClick()
        composeRule.onNodeWithText("OpenSubsonic returned error 40").assertIsDisplayed()

        composeRule.onNodeWithTag("login.submit").performClick()
        waitForLibrary()
    }

    private fun login() {
        enterCredentials()
        composeRule.onNodeWithTag("login.submit").performClick()
        waitForLibrary()
    }

    private fun waitForLibrary() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("library.tracks").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("library.tracks").assertIsDisplayed()
    }

    private fun waitForTrackList() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("track.track-1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("track.track-1").assertIsDisplayed()
    }

    private fun enterCredentials() {
        composeRule.onNodeWithTag("login.server").performTextInput("https://music.test")
        composeRule.onNodeWithTag("login.username").performTextInput("listener")
        composeRule.onNodeWithTag("login.password").performTextInput("secret")
        Espresso.closeSoftKeyboard()
        composeRule.waitForIdle()
    }

    private fun notificationPermissionRule(): TestRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TestRule { statement, _ -> statement }
        }
}
