# Onyx SDK Integration in Notable

This document provides comprehensive documentation on how the Onyx SDK is integrated and used within the Notable project, a note-taking application optimized for Onyx e-ink devices.

## Overview

The Onyx SDK is a proprietary library suite from Onyx International, specifically designed for their e-ink devices (primarily the BOOX series). Notable uses three main SDK components:

- **onyxsdk-base** (v1.8.2.1) - Core functionality and device management
- **onyxsdk-device** (v1.3.1.3) - Device-specific features and EPD control
- **onyxsdk-pen** (v1.4.12.1) - Advanced pen input handling and rendering

## SDK Dependencies

```gradle
dependencies {
    implementation('com.onyx.android.sdk:onyxsdk-device:1.3.1.3') {
        exclude group: 'com.android.support', module: 'support-compat'
    }

    implementation('com.onyx.android.sdk:onyxsdk-pen:1.4.12.1') {
        exclude group: 'com.android.support', module: 'support-compat'
        exclude group: 'com.android.support', module: 'appcompat-v7'
    }

    implementation('com.onyx.android.sdk:onyxsdk-base:1.8.2.1') {
        exclude group: 'com.android.support', module: 'support-compat'
        exclude group: 'com.android.support', module: 'appcompat-v7'
    }
}
```

## Core Components

### 1. TouchHelper - Pen Input Management

The `TouchHelper` class is the central component for handling stylus input on Onyx devices.

#### Initialization
```kotlin
private val touchHelper by lazy {
    referencedSurfaceView = this.hashCode().toString()
    TouchHelper.create(this, inputCallback)
}
```

#### Raw Input Callback Implementation
```kotlin
private val inputCallback: RawInputCallback = object : RawInputCallback() {
    override fun onBeginRawDrawing(p0: Boolean, p1: TouchPoint?) {
        // Called when drawing starts
    }

    override fun onRawDrawingTouchPointListReceived(plist: TouchPointList) {
        // Process touch points for drawing
        when (state.mode) {
            Mode.Draw -> handleDrawing(plist)
            Mode.Erase -> handleErasing(plist)
            Mode.Line -> handleLineDrawing(plist)
        }
    }

    override fun onEndRawDrawing(p0: Boolean, p1: TouchPoint?) {
        // Called when drawing ends
    }
}
```

### 2. EpdController - E-Ink Display Management

Controls the e-ink display refresh behavior for optimal performance and visual quality.

#### Display Schemes
```kotlin
// Set scribble mode for handwriting
EpdController.setDisplayScheme(SCHEME_SCRIBBLE)

// Return to normal mode
EpdController.setDisplayScheme(SCHEME_NORMAL)
```

#### Update Modes
```kotlin
// Different refresh quality levels
enum class UpdateMode {
    GC,           // Full refresh (best quality, slowest)
    GU,           // Grayscale update (preserves tone)
    DU,           // Direct update (faster, partial)
    ANIMATION,    // Smooth animation updates
    A2            // Fastest update (lowest quality)
}
```

#### Animation Mode Toggle
```kotlin
fun setAnimationMode(isAnimationMode: Boolean) {
    if (isAnimationMode) {
        EpdController.applyTransientUpdate(UpdateMode.ANIMATION_X)
    } else {
        EpdController.clearTransientUpdate(true)
    }
}
```

### 3. NeoTools - Advanced Pen Rendering

Onyx provides specialized pen classes for realistic drawing effects.

#### Available NeoTools
```kotlin
import com.onyx.android.sdk.pen.NeoBrushPen
import com.onyx.android.sdk.pen.NeoCharcoalPen
import com.onyx.android.sdk.pen.NeoFountainPen
import com.onyx.android.sdk.pen.NeoMarkerPen
```

#### Usage in Drawing
```kotlin
fun drawStroke(canvas: Canvas, stroke: Stroke) {
    val points = strokeToTouchPoints(stroke)

    when (stroke.pen) {
        Pen.PENCIL -> NeoCharcoalPen.drawNormalStroke(
            null, canvas, paint, points,
            stroke.color, stroke.size,
            ShapeCreateArgs(), Matrix(), false
        )

        Pen.BRUSH -> NeoBrushPen.drawStroke(
            canvas, paint, points, stroke.size, pressure, false
        )

        Pen.MARKER -> NeoMarkerPen.drawStroke(
            canvas, paint, points, stroke.size, false
        )

        Pen.FOUNTAIN -> NeoFountainPen.drawStroke(
            canvas, paint, points, 1f, stroke.size, pressure, false
        )
    }
}
```

## Surface Management

### Surface Setup
```kotlin
fun setupSurface(view: View, touchHelper: TouchHelper, toolbarHeight: Int) {
    touchHelper.debugLog(false)
    touchHelper.setRawDrawingEnabled(false)
    touchHelper.closeRawDrawing()

    // Define drawing area (exclude toolbar)
    val limitRect = Rect(0, toolbarHeight, view.width, view.height)
    val excludeRect = Rect(0, 0, view.width, toolbarHeight)

    touchHelper.setLimitRect(mutableListOf(limitRect))
        .setExcludeRect(listOf(excludeRect))
        .openRawDrawing()

    touchHelper.setRawDrawingEnabled(true)
}
```

### Surface Lifecycle
```kotlin
fun onSurfaceInit(view: View) {
    // Try different update modes for compatibility
    tryToSetRefreshMode(view, UpdateMode.HAND_WRITING_REPAINT_MODE) ||
    tryToSetRefreshMode(view, UpdateMode.REGAL)

    EpdController.enablePost(1)
}

fun onSurfaceChanged(view: View) {
    EpdController.enablePost(view, 1)
}

fun onSurfaceDestroy(view: View, touchHelper: TouchHelper) {
    touchHelper.setRawDrawingEnabled(false)
}
```

## Drawing Workflow

### 1. Input Processing
```kotlin
override fun onRawDrawingTouchPointListReceived(plist: TouchPointList) {
    // Transform coordinates to page space
    val scaledPoints = copyInput(plist.points, page.scroll, page.zoomLevel.value)

    // Handle scribble-to-erase
    val erasedByScribble = handleScribbleToErase(page, scaledPoints, history, pen)

    if (erasedByScribble.isNullOrEmpty()) {
        // Draw new stroke
        handleDraw(page, strokeHistoryBatch, strokeSize, color, pen, scaledPoints)
    }
}
```

### 2. Stroke Rendering
```kotlin
fun handleDraw(
    page: PageView,
    strokeHistoryBatch: MutableList<String>,
    strokeSize: Float,
    color: Int,
    pen: Pen,
    points: List<SimplePointF>
) {
    val stroke = penToStroke(pen, strokeSize, color, points)
    page.strokes.add(stroke)
    strokeHistoryBatch.add(stroke.id)

    // Render to canvas
    drawStroke(page.windowedCanvas, stroke, Offset.Zero)
}
```

### 3. Display Update
```kotlin
fun refreshScreenRegion(view: View, dirtyRect: Rect) {
    EpdController.refreshScreenRegion(
        view,
        dirtyRect.left, dirtyRect.top,
        dirtyRect.width(), dirtyRect.height(),
        UpdateMode.ANIMATION_MONO
    )
}

fun resetScreenFreeze(touchHelper: TouchHelper) {
    touchHelper.isRawDrawingRenderEnabled = false
    touchHelper.isRawDrawingRenderEnabled = true
}
```

## Error Handling and Compatibility

### Graceful Degradation
```kotlin
private fun tryToSetRefreshMode(view: View, mode: UpdateMode): Boolean {
    return try {
        EpdController.setViewDefaultUpdateMode(view, mode)
        true
    } catch (e: NullPointerException) {
        einkLogger.d("Device does not support update mode $mode")
        false
    } catch (e: IllegalArgumentException) {
        einkLogger.d("Device does not support update mode $mode")
        false
    } catch (e: Exception) {
        einkLogger.e("Unexpected error setting update mode $mode", e)
        false
    }
}
```

### Device Detection
```kotlin
// Check if running on Onyx device
val isOnyxDevice = try {
    Device.currentDevice() != null
} catch (e: Exception) {
    false
}
```

## Performance Optimization

### Turbo Mode for Drawing
```kotlin
fun prepareForPartialUpdate(view: View, touchHelper: TouchHelper) {
    EpdController.setDisplayScheme(SCHEME_SCRIBBLE)
    EpdController.enableA2ForSpecificView(view)
    EpdController.setEpdTurbo(100) // Maximum speed

    touchHelper.isRawDrawingRenderEnabled = false
    touchHelper.isRawDrawingRenderEnabled = true
}
```

### Waiting for Refresh Completion
```kotlin
suspend fun waitForEpdRefresh(updateOption: UpdateOption = Device.currentDevice().appScopeRefreshMode) {
    when (updateOption) {
        UpdateOption.NORMAL -> delay(190)
        UpdateOption.REGAL -> delay(180)
        UpdateOption.FAST -> delay(20)
        UpdateOption.FAST_X -> delay(4)
        else -> delay(10)
    }
}
```

## Configuration and Settings

### Pen Style Mapping
```kotlin
fun penToStroke(pen: Pen): Int {
    return when (pen) {
        Pen.BALLPEN -> StrokeStyle.PENCIL
        Pen.PENCIL -> StrokeStyle.CHARCOAL
        Pen.BRUSH -> StrokeStyle.NEO_BRUSH
        Pen.MARKER -> StrokeStyle.MARKER
        Pen.FOUNTAIN -> StrokeStyle.FOUNTAIN
    }
}
```

### NeoTools Toggle
```kotlin
// Some NeoTools are unstable and disabled by default
val useNeoTools = GlobalAppSettings.current.neoTools

if (useNeoTools && pen == Pen.FOUNTAIN) {
    NeoFountainPen.drawStroke(...)
} else {
    drawFountainPenStroke(...) // Custom implementation
}
```

## Best Practices

1. **Always handle exceptions** - Onyx SDK can be unstable
2. **Test on multiple devices** - Behavior varies between Onyx models
3. **Use appropriate refresh modes** - Balance quality vs performance
4. **Proper cleanup** - Close touch helpers and reset display modes
5. **Fallback implementations** - Provide alternatives for unsupported features

## Troubleshooting

### Common Issues

1. **Crashes with NeoTools**
   ```kotlin
   // Disable unstable tools
   val disabledTools = listOf(
       "com.onyx.android.sdk.pen.NeoCharcoalPenV2",
       "com.onyx.android.sdk.pen.NeoMarkerPen",
       "com.onyx.android.sdk.pen.NeoBrushPen"
   )
   ```

2. **Display freezing**
   ```kotlin
   // Ensure proper reset after drawing
   resetScreenFreeze(touchHelper)
   ```

3. **Incompatible update modes**
   ```kotlin
   // Try multiple modes with fallbacks
   tryToSetRefreshMode(view, UpdateMode.HAND_WRITING_REPAINT_MODE) ||
   tryToSetRefreshMode(view, UpdateMode.REGAL)
   ```

## Architecture Integration

The Onyx SDK is deeply integrated into Notable's architecture:

- **DrawCanvas**: Manages touch input and rendering
- **einkHelper**: Handles display optimization
- **penStrokes**: Implements drawing algorithms
- **EditorState**: Manages drawing modes and settings

This integration enables smooth, pressure-sensitive handwriting on Onyx devices while maintaining compatibility with standard Android devices (though with reduced functionality).