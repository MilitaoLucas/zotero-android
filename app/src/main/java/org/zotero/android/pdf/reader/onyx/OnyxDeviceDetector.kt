package org.zotero.android.pdf.reader.onyx

import android.os.Build
import timber.log.Timber

/**
 * Utility to detect if the app is running on an Onyx e-ink device.
 * Uses multiple detection methods for reliability.
 */
object OnyxDeviceDetector {
    private var isOnyxDeviceCache: Boolean? = null

    /**
     * Check if the current device is an Onyx device.
     * Result is cached after first call for performance.
     */
    fun isOnyxDevice(): Boolean {
        if (isOnyxDeviceCache != null) {
            return isOnyxDeviceCache!!
        }

        isOnyxDeviceCache = detectOnyxDevice()
        Timber.d("Onyx device detection result: $isOnyxDeviceCache (Manufacturer: ${Build.MANUFACTURER}, Model: ${Build.MODEL})")

        return isOnyxDeviceCache!!
    }

    /**
     * Detect Onyx device using multiple methods
     */
    private fun detectOnyxDevice(): Boolean {
        // Method 1: Check manufacturer and brand
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()

        if (manufacturer.contains("onyx") || brand.contains("onyx")) {
            Timber.d("Onyx device detected via Build.MANUFACTURER/BRAND")
            return true
        }

        // Method 2: Check for Onyx SDK classes
        val isOnyxSdkAvailable = checkOnyxSdkAvailability()
        if (isOnyxSdkAvailable) {
            Timber.d("Onyx device detected via SDK availability")
            return true
        }

        // Method 3: Check device model patterns
        val model = Build.MODEL.lowercase()
        val onyxModelPatterns = listOf("boox", "nova", "note", "poke", "max", "leaf")
        if (onyxModelPatterns.any { model.contains(it) }) {
            Timber.d("Onyx device detected via model pattern")
            return true
        }

        Timber.d("Not an Onyx device")
        return false
    }

    /**
     * Check if Onyx SDK classes are available
     */
    private fun checkOnyxSdkAvailability(): Boolean {
        return try {
            // Try to load Onyx SDK class without invoking methods
            // This avoids the hidden API restrictions
            Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")
            true
        } catch (e: ClassNotFoundException) {
            false
        } catch (e: Exception) {
            Timber.d("Error checking Onyx SDK availability: ${e.message}")
            false
        }
    }

    /**
     * Reset the cache (useful for testing)
     */
    fun resetCache() {
        isOnyxDeviceCache = null
    }
}

