# PaperFlow

PaperFlow is a premium Android document scanner app built with Kotlin, Jetpack Compose, and CameraX. Think CamScanner — but offline, no ads, no account required.

## Features

### Phase 1+2: Scanner Foundation
- **Multi-page scanning** — Capture multiple pages with thumbnail strip
- **Document library** — Home screen with search, rename, delete
- **Page review** — Reorder pages, delete pages, add more

### Phase 3+4: Image Enhancement & Crop
- **11 Filters** — Auto, Magic Color, Grayscale, B&W, Hi-Contrast, Lighten, Sharpen, No Shadow, Stamp, Invert
- **6 Adjustments** — Brightness, Contrast, Saturation, Sharpness, Warmth, Exposure
- **Auto Enhance** — One-tap smart enhancement
- **Perspective Crop** — 4-corner draggable handles with perspective correction
- **Rotation** — 90° CW/CCW, flip horizontal/vertical

### Phase 5: PDF Export
- **Export as PDF** — Page sizes: Auto, A4, Letter, Legal
- **Quality options** — Small, Balanced, High Quality
- **Export as JPG** — Individual page images
- **Share** — Via Android share sheet (FileProvider)

### Phase 6: PDF Tools
- **Merge PDFs** — Combine 2+ PDF files
- **Split PDF** — Split into individual pages
- **Compress** — Reduce PDF file size
- **Images → PDF** — Convert images to PDF

### Phase 7: Signature & Annotation
- **Signature pad** — Draw with finger, choose ink color + width
- **Saved signatures** — Store and reuse signatures (Room database)
- **Annotations** — Pen and Highlighter tools with 5 colors
- **Flatten to bitmap** — Annotations saved directly onto page images

## Tech Stack

- Kotlin + Jetpack Compose + Material 3
- CameraX for camera
- Room for local database
- Coil 3 for image loading
- Compose Navigation
- Apache PDFBox Android for PDF tools
- Android PdfDocument for PDF export

## Setup

1. Open `D:\Codex` in Android Studio
2. Let Gradle sync
3. Connect a device with USB debugging
4. Click Run

## Roadmap

- Phase 8: OCR text recognition
- Phase 9: Watermark + Batch processing
