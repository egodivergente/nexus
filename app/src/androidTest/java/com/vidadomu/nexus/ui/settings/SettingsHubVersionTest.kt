package com.vidadomu.nexus.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.vidadomu.nexus.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHubVersionTest {
    @Test fun systemRowVersionMatchesInstalledPackageInfo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            else @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0)
        val installedCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        assertEquals(info.versionName, BuildConfig.VERSION_NAME)
        assertEquals(installedCode, BuildConfig.VERSION_CODE.toLong())
        assertTrue(systemHubSummary(BuildConfig.VERSION_NAME, BuildConfig.CODENAME, false).contains(info.versionName!!))
    }
}
