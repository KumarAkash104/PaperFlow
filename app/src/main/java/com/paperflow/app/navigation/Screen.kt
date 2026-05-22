package com.paperflow.app.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")

    data object Camera : Screen("camera/{documentId}?mode={mode}") {
        fun createRoute(documentId: String = "new", mode: String = "scan") = "camera/$documentId?mode=$mode"
    }

    data object Review : Screen("review/{documentId}") {
        fun createRoute(documentId: String) = "review/$documentId"
    }

    data object Editor : Screen("editor/{pageId}") {
        fun createRoute(pageId: String) = "editor/$pageId"
    }

    data object Export : Screen("export/{documentId}") {
        fun createRoute(documentId: String) = "export/$documentId"
    }

    data object PdfTools : Screen("pdf_tools")

    data object SignaturePad : Screen("signature_pad/{pageId}") {
        fun createRoute(pageId: String) = "signature_pad/$pageId"
    }

    data object SignaturePlacement : Screen("signature_place/{pageId}") {
        fun createRoute(pageId: String) = "signature_place/$pageId"
    }

    data object Annotate : Screen("annotate/{pageId}") {
        fun createRoute(pageId: String) = "annotate/$pageId"
    }

    data object Settings : Screen("settings")

    data object PdfEditor : Screen("pdf_editor/{pdfPath}") {
        fun createRoute(pdfPath: String) = "pdf_editor/${android.net.Uri.encode(pdfPath)}"
    }

    data object PdfReader : Screen("pdf_reader/{pdfPath}") {
        fun createRoute(pdfPath: String) = "pdf_reader/${android.net.Uri.encode(pdfPath)}"
    }

    data object PhotoEditor : Screen("photo_editor/{photoId}") {
        fun createRoute(photoId: String) = "photo_editor/$photoId"
    }

    data object PhotoResize : Screen("photo_resize?photoId={photoId}") {
        fun createRoute(photoId: String? = null) = if (photoId != null) "photo_resize?photoId=$photoId" else "photo_resize"
    }
}
