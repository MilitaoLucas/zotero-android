package org.zotero.android.pdf.reader.onyx

import timber.log.Timber

/**
 * Utility to detect if the app is running on an Onyx e-ink device.
 * Uses reflection to safely check for Onyx SDK availability.
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

        isOnyxDeviceCache = try {
            // Use reflection to avoid crashes on non-Onyx devices
            val deviceClass = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")
            val method = deviceClass.getMethod("isEinkScreen")
            val result = method.invoke(null) as? Boolean
            Timber.d("Onyx device detection: ${result ?: false}")
            result ?: false
        } catch (e: ClassNotFoundException) {
            Timber.d("Onyx SDK not available - ClassNotFoundException")
            false
        } catch (e: NoSuchMethodException) {
            Timber.d("Onyx SDK method not found - NoSuchMethodException")
            false
        } catch (e: Exception) {
            Timber.w(e, "Error detecting Onyx device")
            false
        }

        return isOnyxDeviceCache!!
    }

    /**
     * Reset the cache (useful for testing)
     */
    fun resetCache() {
        isOnyxDeviceCache = null
    }
}

