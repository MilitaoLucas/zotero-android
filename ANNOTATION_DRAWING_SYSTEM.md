# Zotero Android - Annotation Drawing System

## Overview

The Zotero Android app uses **PSPDFKit** library for PDF rendering and annotation drawing. The annotation system is divided into several layers:

1. **UI Layer** (Jetpack Compose) - Display and editing interfaces
2. **ViewModel Layer** - Business logic and state management
3. **Data Layer** - Annotation models and conversion
4. **PSPDFKit Integration** - Native PDF annotation drawing
5. **Preview/Cache Layer** - Bitmap generation and caching

---

## Architecture Layers

### 1. PSPDFKit Integration (Core Drawing Engine)

**Location**: `/app/src/main/java/org/zotero/android/pdf/reader/PdfReaderViewModel.kt`

The app uses PSPDFKit's native `PdfFragment` which handles the actual drawing of annotations on the PDF canvas. Key components:

#### PdfFragment Configuration
```kotlin
private lateinit var pdfFragment: PdfFragment
```

The `PdfFragment` is initialized via `PdfUiFragment` and provides:
- Canvas rendering
- Touch/gesture handling
- Annotation tool modes
- Undo/redo functionality

#### Annotation Tool Configuration

Each annotation type is configured with drawing properties:

**Ink Annotations (freehand drawing)**:
```kotlin
private fun configureInk(drawColor: Int?, activeLineWidth: Float) {
    pdfFragment.annotationConfiguration.put(
        AnnotationTool.INK,
        InkAnnotationConfiguration.builder(context)
            .setDefaultColor(drawColor)
            .setDefaultThickness(activeLineWidth)
            .build()
    )
}
```

**Highlight Annotations**:
```kotlin
private fun configureHighlight(drawColor: Int?) {
    pdfFragment.annotationConfiguration.put(
        AnnotationTool.HIGHLIGHT,
        MarkupAnnotationConfiguration.builder(context, AnnotationTool.HIGHLIGHT)
            .setDefaultColor(drawColor)
            .build()
    )
}
```

**Text Annotations (FreeText)**:
```kotlin
private fun configureFreeText(drawColor: Int?, textSize: Float) {
    pdfFragment.annotationConfiguration.put(
        AnnotationTool.FREETEXT,
        FreeTextAnnotationConfiguration.builder(context)
            .setDefaultColor(drawColor)
            .setDefaultTextSize(textSize)
            .build()
    )
}
```

**Note Annotations**:
```kotlin
private fun configureNote(drawColor: Int?) {
    pdfFragment.annotationConfiguration.put(
        AnnotationTool.NOTE,
        NoteAnnotationConfiguration.builder(context)
            .setDefaultColor(drawColor)
            .build()
    )
}
```

**Square Annotations (image areas)**:
```kotlin
private fun configureSquare(drawColor: Int?) {
    pdfFragment.annotationConfiguration.put(
        AnnotationType.SQUARE,
        ShapeAnnotationConfiguration.builder(context, AnnotationType.SQUARE)
            .setDefaultColor(drawColor)
            .build()
    )
}
```

**Underline Annotations**:
```kotlin
private fun configureUnderline(drawColor: Int?) {
    pdfFragment.annotationConfiguration.put(
        AnnotationTool.UNDERLINE,
        NoteAnnotationConfiguration.builder(context)
            .setDefaultColor(drawColor)
            .build()
    )
}
```

**Eraser Tool**:
```kotlin
private fun configureEraser(activeEraserSize: Float) {
    pdfFragment.annotationConfiguration.put(
        AnnotationTool.ERASER,
        EraserToolConfiguration.builder()
            .setDefaultThickness(activeEraserSize)
            .build()
    )
}
```

#### Annotation Tool Activation

```kotlin
private fun updateAnnotationToolDrawColorAndSize(
    annotationTool: AnnotationTool,
    drawColor: Int?
) {
    pdfFragment.exitCurrentlyActiveMode()
    when (annotationTool) {
        AnnotationTool.INK -> configureInk(drawColor, this.activeLineWidth)
        AnnotationTool.FREETEXT -> configureFreeText(drawColor, this.activeFontSize)
        AnnotationTool.HIGHLIGHT -> configureHighlight(drawColor)
        AnnotationTool.NOTE -> configureNote(drawColor)
        AnnotationTool.SQUARE -> configureSquare(drawColor)
        AnnotationTool.UNDERLINE -> configureUnderline(drawColor)
        AnnotationTool.ERASER -> configureEraser(this.activeEraserSize)
        else -> {}
    }
    pdfFragment.enterAnnotationCreationMode(annotationTool)
}
```

The actual drawing on the PDF canvas is handled internally by PSPDFKit's native code.

---

### 2. Annotation Preview Generation

**Location**: `/app/src/main/java/org/zotero/android/pdf/data/AnnotationPreviewManager.kt`

This manager creates bitmap previews of annotations for the sidebar/list view.

#### Key Methods

**Store Preview**:
```kotlin
fun store(
    rawDocument: PdfDocument,
    annotation: Annotation,
    parentKey: String,
    libraryId: LibraryIdentifier,
    isDark: Boolean,
    annotationMaxSideSize: Int,
)
```

**Bitmap Generation Process**:
1. Extract the annotation's bounding box from the PDF
2. Render that region of the PDF page to a bitmap
3. For Ink and FreeText annotations, draw the annotation on top
4. Scale the bitmap to fit the sidebar dimensions
5. Cache to memory and disk

#### Drawing Annotation on Bitmap

```kotlin
private fun drawAnnotationOnBitmap(
    sourceBitmap: Bitmap,
    annotation: Annotation
) {
    // Create temporary bitmap for annotation
    val annotationBitmap = createBitmap(sourceBitmap.width, sourceBitmap.height)
    
    // PSPDFKit renders annotation to bitmap
    annotation.renderToBitmap(annotationBitmap)
    
    // Composite annotation onto source
    val canvas = Canvas(sourceBitmap)
    canvas.drawBitmap(
        annotationBitmap,
        null,
        Rect(0, 0, sourceBitmap.width, sourceBitmap.height),
        null
    )
    annotationBitmap.recycle()
}
```

**Bitmap Scaling**:
```kotlin
private fun generateRawDocumentBitmap(
    annotation: Annotation,
    rawDocument: PdfDocument,
    maxSide: Int,
): Bitmap {
    val annotationRect = annotation.boundingBox
    val width = (annotationRect.right - annotationRect.left).toInt()
    val height = (annotationRect.top - annotationRect.bottom).toInt()
    
    // Render PDF region to bitmap
    val rawDocumentBitmap = rawDocument.renderPageToBitmap(...)
    
    // Scale to fit sidebar
    val scaleX = width / maxSide.toDouble()
    val scaleY = height / maxSide.toDouble()
    val resultScale = scaleX.coerceAtLeast(scaleY)
    val resultWidth = (width / resultScale).toInt()
    val resultHeight = (height / resultScale).toInt()
    
    return rawDocumentBitmap.scale(resultWidth, resultHeight, true)
}
```

---

### 3. Color Management

**Location**: `/app/src/main/java/org/zotero/android/sync/AnnotationColorGenerator.kt`

Handles color transformations for different annotation types and themes.

#### Color Processing

```kotlin
fun color(
    colorHex: String,
    type: AnnotationType?,
    isDarkMode: Boolean
): Triple<Int, Float, BlendMode?> {
    val colorInt = colorHex.toColorInt()
    var opacity = 1F
    
    when (type) {
        AnnotationType.note, AnnotationType.image, 
        AnnotationType.ink, AnnotationType.text -> {
            return Triple(colorInt, 1F, null)
        }
        
        AnnotationType.highlight -> {
            opacity = if (isDarkMode) highlightDarkOpacity else highlightOpacity
        }
        
        AnnotationType.underline -> {
            opacity = if (isDarkMode) underlineDarkOpacity else underlineOpacity
        }
    }
    
    // Apply dark mode adjustments
    val adjustedColor = if (isDarkMode) {
        adjustColorForDarkMode(colorInt, opacity)
    } else {
        applyOpacity(colorInt, opacity)
    }
    
    return Triple(adjustedColor, opacity, blendMode(isDarkMode, type))
}
```

**Blend Modes**:
- Highlights use `LIGHTEN` in dark mode, `MULTIPLY` in light mode
- Ink, Note, Image, Text annotations have no blend mode (opaque)

---

### 4. Data Models

#### PDFAnnotation Interface

**Location**: `/app/src/main/java/org/zotero/android/pdf/data/PDFAnnotation.kt`

```kotlin
interface PDFAnnotation {
    val key: String
    val type: AnnotationType
    val lineWidth: Float?
    val page: Int
    val pageLabel: String
    val comment: String
    val color: String
    val text: String?
    val fontSize: Float?
    val rotation: Int?
    val sortIndex: String
    val tags: List<Tag>
    val isZoteroAnnotation: Boolean
    
    fun paths(boundingBoxConverter: AnnotationBoundingBoxConverter): List<List<PointF>>
    fun rects(boundingBoxConverter: AnnotationBoundingBoxConverter): List<RectF>
    fun boundingBox(boundingBoxConverter: AnnotationBoundingBoxConverter): RectF
}
```

Two implementations:
1. **PDFDocumentAnnotation** - Annotations loaded from PDF document
2. **PDFDatabaseAnnotation** - Annotations stored in Zotero database

#### Coordinate Conversion

**Location**: `/app/src/main/java/org/zotero/android/pdf/data/AnnotationBoundingBoxConverter.kt`

Converts between PDF coordinate space and database coordinate space:

```kotlin
class AnnotationBoundingBoxConverter(val document: PdfDocument) {
    // PDF coordinates -> Database coordinates
    fun convertToDb(rect: RectF, page: Int): RectF?
    fun convertToDb(point: PointF, page: Int): PointF?
    
    // Database coordinates -> PDF coordinates
    fun convertFromDb(rect: RectF, page: Int): RectF?
    fun convertFromDb(point: PointF, page: Int): PointF?
}
```

This handles PDF page rotations and different coordinate systems.

---

### 5. UI Layer (Jetpack Compose)

#### Main Annotation Screen

**Location**: `/app/src/main/java/org/zotero/android/pdf/annotation/PdfAnnotationScreen.kt`

Displays annotation details and editing controls:

```kotlin
@Composable
internal fun PdfAnnotationScreen(
    viewModel: PdfAnnotationViewModel,
    args: PdfAnnotationArgs,
    navigateToTagPicker: () -> Unit,
    onBack: () -> Unit,
)
```

Different row types for each annotation:
- `PdfAnnotationNoteRow` - Note annotations
- `PdfAnnotationHighlightRow` - Highlight annotations
- `PdfAnnotationInkRow` - Freehand ink drawings
- `PdfAnnotationImageRow` - Image area annotations
- `PdfAnnotationUnderlineRow` - Underline annotations
- `PdfAnnotationTextRow` - Text annotations

#### UI Components

**Color Picker**:
**Location**: `/app/src/main/java/org/zotero/android/pdf/annotation/blocks/PdfAnnotationColorPicker.kt`

```kotlin
@Composable
internal fun PdfAnnotationColorPicker(
    colors: List<String>,
    onColorSelected: (color: String) -> Unit,
    selectedColor: String,
)
```

**Size Selector** (for ink/line width):
**Location**: `/app/src/main/java/org/zotero/android/pdf/annotation/blocks/PdfAnnotationSizeSelector.kt`

```kotlin
@Composable
internal fun PdfAnnotationSizeSelector(
    size: Float,
    onSizeChanged: (Float) -> Unit,
)
```

Uses a Slider component with range 0.5f to 25f.

**Color Circle**:
**Location**: `/app/src/main/java/org/zotero/android/pdf/annotation/blocks/PdfAnnotationFilterCircle.kt`

```kotlin
@Composable
internal fun PdfAnnotationFilterCircle(
    hex: String, 
    isSelected: Boolean, 
    onClick: () -> Unit
) {
    Canvas(modifier = Modifier.size(28.dp)) {
        drawCircle(color = Color(hex.toColorInt()))
        if (isSelected) {
            drawCircle(
                color = CustomPalette.White,
                radius = 11.dp.toPx(),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
```

---

### 6. Annotation Types

The system supports 6 annotation types:

1. **INK** - Freehand drawing with paths
   - Properties: `lineWidth`, `color`, `paths`
   - Drawing: Multiple stroke paths

2. **HIGHLIGHT** - Text highlighting
   - Properties: `color`, `opacity`, `blendMode`, `rects`
   - Drawing: Semi-transparent rectangles over text

3. **NOTE** - Sticky note annotations
   - Properties: `color`, `comment`
   - Drawing: Icon at point

4. **UNDERLINE** - Text underlining
   - Properties: `color`, `rects`
   - Drawing: Lines under text

5. **TEXT/FREETEXT** - Text box annotations
   - Properties: `fontSize`, `color`, `text`, `rotation`
   - Drawing: Text rendered at rotation angle

6. **IMAGE/SQUARE** - Image area selection
   - Properties: `color`, `rects`
   - Drawing: Rectangle outline

---

## Drawing Flow

### Creating a New Annotation

1. **User selects tool** (e.g., INK, HIGHLIGHT)
   - `PdfReaderViewModel.updateAnnotationToolDrawColorAndSize()` called
   
2. **Tool configuration applied**
   - Color, size, and other properties set via PSPDFKit configuration builders
   
3. **Enter creation mode**
   - `pdfFragment.enterAnnotationCreationMode(annotationTool)`
   - PSPDFKit activates touch handlers for that tool
   
4. **User draws on PDF**
   - PSPDFKit handles touch events
   - Renders annotation in real-time on canvas
   - All drawing happens in PSPDFKit native code
   
5. **Annotation created**
   - PSPDFKit fires annotation created event
   - `add(annotations)` method processes new annotations
   
6. **Transform and store**
   - Split multi-page annotations if needed
   - Convert to database format via `AnnotationConverter`
   - Store to Realm database
   
7. **Generate preview**
   - `AnnotationPreviewManager.store()` creates bitmap preview
   - Cache to memory and disk
   
8. **Update UI**
   - Sidebar list refreshed with new annotation
   - Thumbnail regenerated if needed

### Editing an Existing Annotation

1. **User selects annotation**
   - `PdfAnnotationScreen` displays annotation details
   
2. **User changes properties** (color, size, comment, tags)
   - UI components emit changes
   - ViewModel updates state
   
3. **Apply changes**
   - Update PSPDFKit annotation object
   - Update database record
   - Regenerate preview bitmap
   
4. **Sync changes**
   - Changes queued for sync to Zotero servers

---

## Key Files Summary

### Core Drawing
- `PdfReaderViewModel.kt` - Main controller, PSPDFKit integration
- `PdfReaderPspdfKitView.kt` - Compose integration with PSPDFKit Fragment

### Preview Generation
- `AnnotationPreviewManager.kt` - Bitmap generation from annotations
- `AnnotationBoundingBoxConverter.kt` - Coordinate space conversions

### Data Models
- `PDFAnnotation.kt` - Annotation interface
- `PDFDocumentAnnotation.kt` - Document-based annotations
- `PDFDatabaseAnnotation.kt` - Database-stored annotations

### Color/Appearance
- `AnnotationColorGenerator.kt` - Color transformations and blend modes

### UI Components
- `PdfAnnotationScreen.kt` - Main annotation editing screen
- `PdfAnnotationColorPicker.kt` - Color selection
- `PdfAnnotationSizeSelector.kt` - Size/width selection
- `PdfAnnotationFilterCircle.kt` - Color circle visualization
- Row components: `PdfAnnotationInkRow.kt`, `PdfAnnotationHighlightRow.kt`, etc.

---

## Technical Details

### Canvas Drawing (PSPDFKit Internal)

The actual rendering to the PDF canvas is handled by PSPDFKit's native rendering engine. The Android app:

1. Configures annotation tools with properties (color, size, etc.)
2. Activates tool modes via `enterAnnotationCreationMode()`
3. PSPDFKit handles:
   - Touch event capture
   - Path/shape generation
   - Canvas rendering
   - Anti-aliasing
   - Layer compositing

### Coordinate Systems

Three coordinate systems are used:

1. **PDF Coordinates** - PSPDFKit native coordinates
2. **Normalized Coordinates** - Page-rotation independent (0-1 range)
3. **Database Coordinates** - Stored in Zotero database

The `AnnotationBoundingBoxConverter` handles all transformations.

### Performance Optimizations

1. **Memory Cache** - Recent annotation previews kept in memory
2. **Disk Cache** - Previews cached to avoid regeneration
3. **Lazy Generation** - Previews only generated when needed
4. **Bitmap Scaling** - Previews scaled to sidebar dimensions
5. **Debouncing** - Annotation changes debounced to reduce operations

---

## Summary

The Zotero Android annotation drawing system is a multi-layered architecture:

- **PSPDFKit** provides the core PDF rendering and annotation drawing capabilities
- **AnnotationPreviewManager** generates bitmap previews for the UI
- **AnnotationColorGenerator** handles theme-aware color transformations
- **Compose UI** provides modern, reactive editing interfaces
- **Realm Database** stores annotation data for sync

The actual drawing on the PDF canvas is abstracted by PSPDFKit - the app configures annotation tools and properties, then PSPDFKit handles all low-level rendering, touch handling, and canvas operations.

