/*
 * DzCentrex : modification de Linphone Android (GPLv3), voir LISEZMOI-DZCENTREX.md.
 *
 * Lecture du QR code de configuration dans une image du téléphone (capture d'écran, image reçue par e-mail ou
 * messagerie), choisie avec le sélecteur de photos d'Android : aucun droit d'accès aux photos n'est demandé.
 * Décodage par ZXing (Apache 2.0). À appeler hors du fil de l'interface.
 */
package org.linphone.dzcentrex

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.WorkerThread
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import org.linphone.core.tools.Log

object LectureQrImage {
    private const val TAG = "[DzCentrex QR image]"

    // Assez pour un QR pris en photo ou en capture d'écran, sans charger une photo de 50 Mpx en mémoire.
    private const val COTE_MAX = 1600

    private val INDICES = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true
    )

    /** Contenu du QR code de l'image, ou null si l'image est illisible ou n'en contient pas. */
    @WorkerThread
    fun lire(context: Context, uri: Uri): String? {
        val image = charger(context, uri) ?: return null
        try {
            val largeur = image.width
            val hauteur = image.height
            val pixels = IntArray(largeur * hauteur)
            image.getPixels(pixels, 0, largeur, 0, 0, largeur, hauteur)
            val source = RGBLuminanceSource(largeur, hauteur, pixels)
            // Hybride d'abord (photos), puis histogramme global (captures d'écran nettes), puis en négatif
            // (QR clair sur fond sombre, mode sombre du portail).
            val essais = listOf(
                BinaryBitmap(HybridBinarizer(source)),
                BinaryBitmap(GlobalHistogramBinarizer(source)),
                BinaryBitmap(HybridBinarizer(source.invert()))
            )
            val lecteur = MultiFormatReader().apply { setHints(INDICES) }
            for (essai in essais) {
                try {
                    return lecteur.decodeWithState(essai).text
                } catch (_: NotFoundException) {
                } finally {
                    lecteur.reset()
                }
            }
            Log.w("$TAG Aucun QR code trouvé dans l'image")
            return null
        } finally {
            image.recycle()
        }
    }

    private fun charger(context: Context, uri: Uri): Bitmap? {
        return try {
            val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bornes) }
            if (bornes.outWidth <= 0 || bornes.outHeight <= 0) {
                Log.e("$TAG Image illisible [$uri]")
                return null
            }
            var reduction = 1
            while (maxOf(bornes.outWidth, bornes.outHeight) / reduction > COTE_MAX) reduction *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = reduction }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) {
            Log.e("$TAG Lecture de l'image impossible : $e")
            null
        }
    }
}
