package org.zotero.android.pdf.reader.onyx

import android.view.MotionEvent
import timber.log.Timber

/**
 * Detects when stylus vs finger is being used and triggers appropriate callbacks.
 * This enables automatic tool switching on Onyx devices.
 */
class OnyxPenDetector(
    private val onPenDetected: () -> Unit,
    private val onFingerDetected: () -> Unit
) {
    private var lastInputWasPen = false

    /**
     * Check if the last input was from a pen
     */
    fun isPenActive(): Boolean = lastInputWasPen

    /**
     * Process a motion event to detect pen vs finger input.
     * Returns false to allow the event to continue processing.
     */
    fun handleMotionEvent(event: MotionEvent): Boolean {
        // Check if input is from stylus
        val isPen = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS

        // Only trigger callbacks when input type CHANGES
        if (isPen && !lastInputWasPen) {
            Timber.d("OnyxPenDetector: Pen detected - switching to drawing mode")
            lastInputWasPen = true
            onPenDetected()
        } else if (!isPen && lastInputWasPen) {
            Timber.d("OnyxPenDetector: Finger detected - switching back to previous mode")
            lastInputWasPen = false
            onFingerDetected()
        }
        // If input type hasn't changed, do nothing - allows continuous drawing

        return false // Allow event to continue
    }

    /**
     * Reset the detection state
     */
    fun reset() {
        lastInputWasPen = false
    }
}

