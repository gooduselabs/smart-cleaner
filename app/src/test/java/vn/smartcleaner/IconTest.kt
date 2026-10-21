package vn.smartcleaner

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w200dp-h200dp-xxhdpi", application = android.app.Application::class)
class IconTest {
    @get:Rule val rule = createComposeRule()
    @Test fun icon() {
        rule.setContent {
            Box(Modifier.size(128.dp).clip(RoundedCornerShape(30.dp))) {
                Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(176.dp))
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(176.dp))
            }
        }
        rule.onRoot().captureRoboImage((System.getProperty("screens.dir") ?: "build") + "/../app/docs/icon.png")
    }
}
