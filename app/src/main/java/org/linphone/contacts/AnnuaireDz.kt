/*
 * DzCentrex : annuaire des collègues (postes du même client), chargé par l'appli elle-même.
 *
 * L'adresse est celle donnée par le QR ([misc] contacts-vcard-list, ex. https://…/prov/annuaire) ; le serveur
 * demande une authentification Digest avec le compte SIP du poste (HA1 du QR). Les vCards sont rangées dans la
 * liste « DzCentrex » (onglet Entreprise des contacts), avec abonnement à l'état de chaque collègue.
 *
 * Code ajouté à Linphone Android (GPLv3).
 */
package org.linphone.contacts

import androidx.annotation.WorkerThread
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Core
import org.linphone.core.FriendList
import org.linphone.core.tools.Log

object AnnuaireDz {
    private const val TAG = "[Annuaire DzCentrex]"
    const val NOM_LISTE = "DzCentrex"

    @WorkerThread
    fun charger(core: Core) {
        val url = core.config.getString("misc", "contacts-vcard-list", null)
        val auth = core.authInfoList.firstOrNull { !it.ha1.isNullOrEmpty() }
        if (url.isNullOrEmpty() || auth == null) {
            Log.i("$TAG Pas d'annuaire (adresse ou compte absent)")
            return
        }
        val utilisateur = auth.username ?: return
        val ha1 = auth.ha1.orEmpty()
        Thread {
            try {
                val vcards = telecharger(url, utilisateur, ha1)
                coreContext.postOnCoreThread { c -> importer(c, vcards) }
            } catch (e: Exception) {
                Log.e("$TAG Échec du téléchargement : $e")
            }
        }.start()
    }

    private fun telecharger(adresse: String, utilisateur: String, ha1: String): String {
        val url = URL(adresse)
        var connexion = url.openConnection() as HttpURLConnection
        connexion.connectTimeout = 10000
        connexion.readTimeout = 10000
        if (connexion.responseCode == 401) {
            val defi = connexion.getHeaderField("WWW-Authenticate").orEmpty()
            connexion.disconnect()
            val p = Regex("(\\w+)=\"?([^\",]*)\"?").findAll(defi).associate { it.groupValues[1] to it.groupValues[2] }
            val realm = p["realm"].orEmpty()
            val nonce = p["nonce"].orEmpty()
            val uri = url.file.ifEmpty { "/" }
            val cnonce = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            val nc = "00000001"
            val ha2 = md5("GET:$uri")
            val reponse = md5("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
            connexion = url.openConnection() as HttpURLConnection
            connexion.connectTimeout = 10000
            connexion.readTimeout = 10000
            connexion.setRequestProperty(
                "Authorization",
                "Digest username=\"$utilisateur\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", " +
                    "algorithm=MD5, qop=auth, nc=$nc, cnonce=\"$cnonce\", response=\"$reponse\""
            )
        }
        val code = connexion.responseCode
        if (code != 200) {
            connexion.disconnect()
            throw IllegalStateException("HTTP $code")
        }
        val texte = connexion.inputStream.bufferedReader().use { it.readText() }
        connexion.disconnect()
        return texte
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    @WorkerThread
    private fun importer(core: Core, vcards: String) {
        val liste = core.getFriendListByName(NOM_LISTE) ?: core.createFriendList().also {
            it.displayName = NOM_LISTE
            it.type = FriendList.Type.Default
            core.addFriendList(it)
        }
        for (f in liste.friends) {
            liste.removeFriend(f)
        }
        val n = if (vcards.isBlank()) 0 else liste.importFriendsFromVcard4Buffer(vcards)
        for (f in liste.friends) {
            f.edit()
            f.isSubscribesEnabled = true
            f.done()
        }
        liste.isSubscriptionsEnabled = true
        liste.updateSubscriptions()
        Log.i("$TAG $n collègue(s) chargé(s)")
        coreContext.contactsManager.notifyContactsListChanged()
    }
}
