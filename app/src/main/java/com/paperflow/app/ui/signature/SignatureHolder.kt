package com.paperflow.app.ui.signature

import android.graphics.Bitmap

/**
 * Temporary holder for passing a signature bitmap between
 * SignaturePadScreen → SignaturePlacementScreen without serialization.
 */
object SignatureHolder {
    var signatureBitmap: Bitmap? = null

    fun set(bitmap: Bitmap) {
        signatureBitmap = bitmap
    }

    fun get(): Bitmap? = signatureBitmap

    fun clear() {
        signatureBitmap = null
    }

    fun getAndClear(): Bitmap? {
        val bmp = signatureBitmap
        signatureBitmap = null
        return bmp
    }
}
