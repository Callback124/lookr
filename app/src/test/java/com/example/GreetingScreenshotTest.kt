package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.local.WatchlistDao
import com.example.data.local.WatchlistEntity
import com.example.data.repository.MediaRepository
import com.example.ui.MovieAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MovieViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val fakeDao = object : WatchlistDao {
    override fun getAllWatchlist(): Flow<List<WatchlistEntity>> = flowOf(emptyList())
    override fun isWatchlisted(id: String): Flow<Boolean> = flowOf(false)
    override suspend fun addToWatchlist(item: WatchlistEntity) {}
    override suspend fun removeFromWatchlist(id: String) {}
  }

  @Test
  fun greeting_screenshot() {
    val repository = MediaRepository(fakeDao)
    val viewModel = MovieViewModel(repository)

    composeTestRule.setContent {
      MyApplicationTheme {
        MovieAppScreen(viewModel = viewModel)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

