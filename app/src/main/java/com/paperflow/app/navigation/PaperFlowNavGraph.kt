package com.paperflow.app.navigation

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.paperflow.app.data.local.AppDatabase
import com.paperflow.app.data.model.SignatureEntity
import com.paperflow.app.data.repository.DocumentRepository
import com.paperflow.app.ui.camera.CameraScreen
import com.paperflow.app.ui.camera.CameraViewModel
import com.paperflow.app.ui.editor.PageEditorScreen
import com.paperflow.app.ui.editor.PageEditorViewModel
import com.paperflow.app.ui.export.ExportScreen
import com.paperflow.app.ui.export.ExportViewModel
import com.paperflow.app.ui.home.HomeScreen
import com.paperflow.app.ui.home.HomeViewModel
import com.paperflow.app.ui.review.ReviewScreen
import com.paperflow.app.ui.review.ReviewViewModel
import com.paperflow.app.ui.settings.SettingsScreen
import com.paperflow.app.ui.signature.AnnotationScreen
import com.paperflow.app.ui.signature.SignatureHolder
import com.paperflow.app.ui.signature.SignaturePadScreen
import com.paperflow.app.ui.signature.SignaturePlacementScreen
import com.paperflow.app.ui.tools.PdfToolsScreen
import com.paperflow.app.ui.tools.PdfToolsViewModel
import com.paperflow.app.ui.editor.PdfEditorScreen
import com.paperflow.app.ui.editor.PdfEditorViewModel
import com.paperflow.app.ui.reader.PdfReaderScreen
import com.paperflow.app.ui.reader.PdfReaderViewModel
import com.paperflow.app.util.ImageProcessor
import com.paperflow.app.pdf.PdfExporter
import com.paperflow.app.pdf.PageSize
import com.paperflow.app.pdf.ExportQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PaperFlowNavGraph(navController: NavHostController) {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(context) }
    val photoRepository = remember { com.paperflow.app.data.repository.PhotoRepository(context) }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300),
                initialOffset = { it / 5 }
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300),
                targetOffset = { it / 5 }
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300),
                initialOffset = { it / 5 }
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300),
                targetOffset = { it / 5 }
            ) + fadeOut(animationSpec = tween(300))
        },
    ) {
        // ── Home ──
        composable(Screen.Home.route) {
            val sharedPrefs = context.getSharedPreferences("paperflow_prefs", android.content.Context.MODE_PRIVATE)
            val viewModel = remember { HomeViewModel(repository, photoRepository, sharedPrefs) }
            val scope = rememberCoroutineScope()
            HomeScreen(
                viewModel = viewModel,
                onScanClick = { mode ->
                    navController.navigate(Screen.Camera.createRoute("new", mode))
                },
                onDocumentClick = { docId ->
                    navController.navigate(Screen.Review.createRoute(docId))
                },
                onDocumentExport = { docId ->
                    navController.navigate(Screen.Export.createRoute(docId))
                },
                onToolsClick = {
                    navController.navigate(Screen.PdfTools.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onDocumentImported = { docId ->
                    navController.navigate(Screen.Review.createRoute(docId))
                },
                onReadPdf = { docId ->
                    navController.navigate(Screen.DocumentReader.createRoute(docId))
                },
                onPhotoClick = { photoId ->
                    navController.navigate(Screen.PhotoEditor.createRoute(photoId))
                }
            )
        }

        // ── Camera ──
        composable(
            route = Screen.Camera.route,
            arguments = listOf(
                navArgument("documentId") {
                    type = NavType.StringType
                    defaultValue = "new"
                },
                navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = "scan"
                }
            ),
        ) { backStack ->
            val docId = backStack.arguments?.getString("documentId")
            val mode = backStack.arguments?.getString("mode") ?: "scan"
            val viewModel = remember(docId, mode) {
                CameraViewModel(repository, photoRepository, docId, mode)
            }
            CameraScreen(
                viewModel = viewModel,
                onClose = { navController.popBackStack() },
                onDone = { savedId ->
                    if (mode == "photo") {
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    } else {
                        navController.navigate(Screen.Review.createRoute(savedId)) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    }
                },
                onEditPage = { pageId ->
                    navController.navigate(Screen.Editor.createRoute(pageId))
                },
                onEditPhoto = { photoId ->
                    navController.navigate(Screen.PhotoEditor.createRoute(photoId))
                }
            )
        }

        // ── Review ──
        composable(
            route = Screen.Review.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.StringType },
            ),
        ) { backStack ->
            val docId = backStack.arguments?.getString("documentId") ?: return@composable
            val sharedPrefs = context.getSharedPreferences("paperflow_prefs", android.content.Context.MODE_PRIVATE)
            val viewModel = remember(docId) { ReviewViewModel(repository, photoRepository, sharedPrefs, docId) }
            ReviewScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onAddMore = { id ->
                    navController.navigate(Screen.Camera.createRoute(id))
                },
                onEditPage = { pageId ->
                    navController.navigate(Screen.Editor.createRoute(pageId))
                },
                onExport = {
                    navController.navigate(Screen.Export.createRoute(docId))
                },
                onSignPage = { pageId ->
                    navController.navigate(Screen.SignaturePad.createRoute(pageId))
                },
                onAnnotatePage = { pageId ->
                    navController.navigate(Screen.Annotate.createRoute(pageId))
                },
            )
        }

        // ── Page Editor ──
        composable(
            route = Screen.Editor.route,
            arguments = listOf(
                navArgument("pageId") { type = NavType.StringType },
            ),
        ) { backStack ->
            val pageId = backStack.arguments?.getString("pageId") ?: return@composable
            val viewModel = remember(pageId) { PageEditorViewModel(repository, pageId) }
            PageEditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSignPage = { id ->
                    navController.navigate(Screen.SignaturePad.createRoute(id))
                },
                onAnnotatePage = { id ->
                    navController.navigate(Screen.Annotate.createRoute(id))
                },
            )
        }

        // ── Export ──
        composable(
            route = Screen.Export.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.StringType },
            ),
        ) { backStack ->
            val docId = backStack.arguments?.getString("documentId") ?: return@composable
            val viewModel = remember(docId) {
                ExportViewModel(repository, docId, context.applicationContext)
            }
            ExportScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        // ── PDF Tools ──
        composable(Screen.PdfTools.route) {
            val viewModel = remember {
                PdfToolsViewModel(context.applicationContext, repository)
            }
            PdfToolsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onImported = { docId ->
                    navController.popBackStack() // pop tools screen
                    navController.navigate(Screen.Review.createRoute(docId))
                },
                onEditPdf = { path ->
                    navController.navigate(Screen.PdfEditor.createRoute(path))
                },
                onReadPdf = { path ->
                    navController.navigate(Screen.PdfReader.createRoute(path))
                },
                onPhotoResizeClick = {
                    navController.navigate(Screen.PhotoResize.createRoute())
                }
            )
        }

        // ── PDF Editor ──
        composable(
            route = Screen.PdfEditor.route,
            arguments = listOf(
                navArgument("pdfPath") { type = NavType.StringType }
            )
        ) { backStack ->
            val pdfPathEncoded = backStack.arguments?.getString("pdfPath") ?: return@composable
            val pdfPath = android.net.Uri.decode(pdfPathEncoded)
            val viewModel = remember(pdfPath) {
                PdfEditorViewModel(context.applicationContext, pdfPath, repository)
            }
            PdfEditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // ── PDF Reader ──
        composable(
            route = Screen.PdfReader.route,
            arguments = listOf(
                navArgument("pdfPath") { type = NavType.StringType }
            )
        ) { backStack ->
            val pdfPathEncoded = backStack.arguments?.getString("pdfPath") ?: return@composable
            val pdfPath = android.net.Uri.decode(pdfPathEncoded)
            val viewModel = remember(pdfPath) {
                PdfReaderViewModel(context.applicationContext, pdfPath, repository)
            }
            PdfReaderScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // ── Document Reader (image-based, no PDF re-export) ──
        composable(
            route = Screen.DocumentReader.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.StringType }
            )
        ) { backStack ->
            val docId = backStack.arguments?.getString("documentId") ?: return@composable

            // Load page paths once and create viewModel with image paths
            var viewModel by remember { mutableStateOf<PdfReaderViewModel?>(null) }

            LaunchedEffect(docId) {
                val doc = withContext(Dispatchers.IO) { repository.getDocument(docId) }
                val pages = withContext(Dispatchers.IO) { repository.getPagesSync(docId) }
                val imagePaths = pages.sortedBy { it.pageIndex }.map { it.imagePath }
                if (imagePaths.isNotEmpty()) {
                    viewModel = PdfReaderViewModel(
                        appContext = context.applicationContext,
                        pdfPath = doc?.title ?: "Document",
                        repository = repository,
                        imagePagePaths = imagePaths,
                        initialDocumentId = docId
                    )
                }
            }

            viewModel?.let { vm ->
                PdfReaderScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ── Settings ──
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ── Signature Pad (step 1: draw) ──
        composable(
            route = Screen.SignaturePad.route,
            arguments = listOf(
                navArgument("pageId") { type = NavType.StringType },
            ),
        ) { backStack ->
            val pageId = backStack.arguments?.getString("pageId") ?: return@composable
            val scope = rememberCoroutineScope()
            val db = remember { AppDatabase.getInstance(context) }

            SignaturePadScreen(
                onBack = { navController.popBackStack() },
                onSave = { signatureBitmap, shouldSave ->
                    if (shouldSave) {
                        // Save signature to Room for reuse (on IO thread)
                        scope.launch(Dispatchers.IO) {
                            try {
                                val sigDir = File(context.filesDir, "signatures")
                                if (!sigDir.exists()) sigDir.mkdirs()
                                val sigFile = File(sigDir, "sig_${System.currentTimeMillis()}.png")
                                sigFile.outputStream().use {
                                    signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                                }
                                db.signatureDao().insertSignature(
                                    SignatureEntity(imagePath = sigFile.absolutePath),
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    // Pass bitmap to placement screen
                    SignatureHolder.set(signatureBitmap)
                    navController.navigate(Screen.SignaturePlacement.createRoute(pageId)) {
                        // Don't stack pad screen under placement
                        popUpTo(Screen.SignaturePad.route) { inclusive = true }
                    }
                },
            )
        }

        // ── Signature Placement (step 2: place on page) ──
        composable(
            route = Screen.SignaturePlacement.route,
            arguments = listOf(
                navArgument("pageId") { type = NavType.StringType },
            ),
        ) { backStack ->
            val pageId = backStack.arguments?.getString("pageId") ?: return@composable
            val scope = rememberCoroutineScope()

            val signatureBitmap = remember { SignatureHolder.get() }

            var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
            var pageImagePath by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(pageId) {
                withContext(Dispatchers.IO) {
                    val page = repository.getPage(pageId)
                    if (page != null) {
                        pageBitmap = ImageProcessor.loadBitmap(page.imagePath, 1500)
                        pageImagePath = page.imagePath
                    }
                }
            }

            if (pageBitmap != null && signatureBitmap != null) {
                SignaturePlacementScreen(
                    pageBitmap = pageBitmap!!,
                    signatureBitmap = signatureBitmap,
                    onBack = {
                        SignatureHolder.clear()
                        navController.popBackStack()
                    },
                    onApply = { resultBitmap ->
                        // Save flattened result back to page file
                        pageImagePath?.let { path ->
                            scope.launch(Dispatchers.IO) {
                                ImageProcessor.saveBitmap(resultBitmap, File(path))
                            }
                        }
                        SignatureHolder.clear()
                        // Go back to review
                        navController.popBackStack()
                    },
                )
            }
        }

        // ── Annotate ──
        composable(
            route = Screen.Annotate.route,
            arguments = listOf(
                navArgument("pageId") { type = NavType.StringType },
            ),
        ) { backStack ->
            val pageId = backStack.arguments?.getString("pageId") ?: return@composable
            val scope = rememberCoroutineScope()

            var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
            var pageImagePath by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(pageId) {
                withContext(Dispatchers.IO) {
                    val page = repository.getPage(pageId)
                    if (page != null) {
                        pageBitmap = ImageProcessor.loadBitmap(page.imagePath, 1500)
                        pageImagePath = page.imagePath
                    }
                }
            }

            pageBitmap?.let { bmp ->
                AnnotationScreen(
                    pageBitmap = bmp,
                    onBack = { navController.popBackStack() },
                    onSave = { resultBitmap ->
                        pageImagePath?.let { path ->
                            scope.launch(Dispatchers.IO) {
                                ImageProcessor.saveBitmap(resultBitmap, File(path))
                            }
                        }
                        navController.popBackStack()
                    },
                )
            }
        }

        // ── Photo Editor ──
        composable(
            route = Screen.PhotoEditor.route,
            arguments = listOf(
                navArgument("photoId") { type = NavType.StringType }
            )
        ) { backStack ->
            val photoId = backStack.arguments?.getString("photoId") ?: return@composable
            val sharedPrefs = context.getSharedPreferences("paperflow_prefs", android.content.Context.MODE_PRIVATE)
            val viewModel = remember(photoId) {
                com.paperflow.app.ui.photo.PhotoEditorViewModel(context.applicationContext, photoRepository, repository, sharedPrefs, photoId)
            }
            com.paperflow.app.ui.photo.PhotoEditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onConvertedToPdf = { docId ->
                    navController.popBackStack()
                    navController.navigate(Screen.Review.createRoute(docId))
                },
                onResizeClick = { pId ->
                    navController.navigate(Screen.PhotoResize.createRoute(pId)) {
                        popUpTo(Screen.PhotoEditor.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Photo Resize ──
        composable(
            route = Screen.PhotoResize.route,
            arguments = listOf(
                navArgument("photoId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStack ->
            val photoId = backStack.arguments?.getString("photoId")
            val viewModel = remember(photoId) {
                com.paperflow.app.ui.photo.PhotoResizeViewModel(context.applicationContext, photoRepository)
            }
            LaunchedEffect(photoId) {
                if (photoId != null) {
                    val photo = photoRepository.getPhoto(photoId)
                    if (photo != null) {
                        viewModel.selectPhoto(android.net.Uri.fromFile(java.io.File(photo.imagePath)))
                    }
                }
            }
            com.paperflow.app.ui.photo.PhotoResizeScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
