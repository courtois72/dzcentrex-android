/*
 * DzCentrex : modification de Linphone Android (GPLv3), voir LISEZMOI-DZCENTREX.md.
 *
 * Réglages du téléphone dont dépend la réception des appels quand l'appli est en arrière-plan :
 * batterie sans restriction (toutes marques), puis démarrage automatique (Xiaomi, Redmi, Poco : MIUI/HyperOS
 * tue l'appli sinon). Demandés une fois par lancement de l'appli, à l'ouverture de l'écran principal.
 */
package org.linphone.dzcentrex

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import org.linphone.R
import org.linphone.core.tools.Log

object ReglagesTelephone {
    private const val TAG = "[DzCentrex Réglages]"
    private const val PREFS = "dzcentrex"
    private const val CLE_XIAOMI = "xiaomi_demarrage_auto_propose"

    private var dejaDemande = false
    private var dialogueOuvert = false

    fun verifier(activity: Activity) {
        if (dialogueOuvert) return
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(activity.packageName)) {
            if (dejaDemande) return
            dejaDemande = true
            Log.w("$TAG Optimisation de la batterie active : demande d'exemption")
            dialogue(activity, R.string.dzcentrex_batterie_titre, R.string.dzcentrex_batterie_message) {
                ouvrirBatterie(activity)
            }
            return
        }
        if (estXiaomi() && !prefs(activity).getBoolean(CLE_XIAOMI, false)) {
            prefs(activity).edit().putBoolean(CLE_XIAOMI, true).apply()
            Log.w("$TAG Xiaomi : proposition du démarrage automatique")
            dialogue(activity, R.string.dzcentrex_xiaomi_titre, R.string.dzcentrex_xiaomi_message) {
                ouvrirDemarrageXiaomi(activity)
            }
        }
    }

    private fun dialogue(activity: Activity, titre: Int, message: Int, action: () -> Unit) {
        dialogueOuvert = true
        AlertDialog.Builder(activity)
            .setTitle(titre)
            .setMessage(message)
            .setPositiveButton(R.string.dzcentrex_ouvrir_reglages) { _, _ -> action() }
            .setNegativeButton(R.string.dzcentrex_plus_tard, null)
            .setOnDismissListener { dialogueOuvert = false }
            .show()
    }

    @SuppressLint("BatteryLife") // Appli de téléphonie : les appels doivent arriver appli fermée.
    private fun ouvrirBatterie(activity: Activity) {
        val demande = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${activity.packageName}"))
        if (!lancer(activity, demande)) {
            lancer(activity, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun ouvrirDemarrageXiaomi(activity: Activity) {
        val autostart = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        )
        if (!lancer(activity, autostart)) {
            // Page de l'appli dans les réglages Android : « Démarrage automatique » y figure sur MIUI/HyperOS.
            lancer(
                activity,
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${activity.packageName}"))
            )
        }
    }

    private fun lancer(activity: Activity, intent: Intent): Boolean {
        return try {
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("$TAG Impossible d'ouvrir $intent : $e")
            false
        }
    }

    private fun estXiaomi(): Boolean =
        Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
