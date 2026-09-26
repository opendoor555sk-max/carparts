package com.kabadimarket.app.data

import android.content.Context
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Opens Google's barcode scanner screen (QR, DataMatrix, Code 128, EAN ...)
 * and returns the scanned text.
 */
fun scanBarcode(context: Context, onResult: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
        .build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode ->
            val value = barcode.rawValue?.trim().orEmpty()
            if (value.isNotEmpty()) onResult(value)
        }
        .addOnFailureListener { e ->
            Toast.makeText(context, "Scanner error: ${e.message}", Toast.LENGTH_LONG).show()
        }
}
