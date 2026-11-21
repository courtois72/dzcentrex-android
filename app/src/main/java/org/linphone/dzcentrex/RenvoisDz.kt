/*
 * DzCentrex : modification de Linphone Android (GPLv3), voir LISEZMOI-DZCENTREX.md.
 *
 * Renvois du poste (écran Renvois du menu) : lus et enregistrés sur le serveur (/prov/renvois, à côté de l'annuaire
 * donné par le QR), avec le compte SIP du poste en Digest comme l'annuaire. Ce sont les mêmes réglages que dans
 * l'espace utilisateur et que les codes du téléphone (*21, *67, *61, *78) : appliqués dès l'appel suivant.
 */
package org.linphone.dzcentrex

import androidx.annotation.WorkerThread
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import org.json.JSONObject
import org.linphone.core.Core

object RenvoisDz {
    class Compte(val adresse: String, val utilisateur: String, val ha1: String)

    class ErreurServeur(val code: Int, message: String) : Exception(message)

    /** Adresse et compte du poste, lus dans la configuration du QR ; null si le poste n'est pas configuré. */
    @WorkerThread
    fun compte(core: Core): Compte? {
        val annuaire = core.config.getString("misc", "contacts-vcard-list", null) ?: return null
        val auth = core.authInfoList.firstOrNull { !it.ha1.isNullOrEmpty() } ?: return null
        val adresse = annuaire.substringBeforeLast("/") + "/renvois"
        return Compte(adresse, auth.username ?: return null, auth.ha1.orEmpty())
    }

    fun lire(c: Compte): JSONObject = JSONObject(requete(c, "GET", null))

    fun enregistrer(c: Compte, renvois: JSONObject): JSONObject = JSONObject(requete(c, "PUT", renvois.toString()))

    private fun ouvrir(url: URL, methode: String, corps: String?, autorisation: String?): HttpURLConnection {
        val connexion = url.openConnection() as HttpURLConnection
        connexion.connectTimeout = 10000
        connexion.readTimeout = 10000
        connexion.requestMethod = methode
        connexion.setRequestProperty("Accept", "application/json")
        if (autorisation != null) connexion.setRequestProperty("Authorization", autorisation)
        if (corps != null) {
            connexion.doOutput = true
            connexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connexion.outputStream.use { it.write(corps.toByteArray()) }
        }
        return connexion
    }

    private fun requete(c: Compte, methode: String, corps: String?): String {
        val url = URL(c.adresse)
        var connexion = ouvrir(url, methode, corps, null)
        if (connexion.responseCode == 401) {
            val defi = connexion.getHeaderField("WWW-Authenticate").orEmpty()
            connexion.disconnect()
            val p = Regex("(\\w+)=\"?([^\",]*)\"?").findAll(defi).associate { it.groupValues[1] to it.groupValues[2] }
            val uri = url.file.ifEmpty { "/" }
            val cnonce = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            val nc = "00000001"
            val ha2 = md5("$methode:$uri")
            val reponse = md5("${c.ha1}:${p["nonce"].orEmpty()}:$nc:$cnonce:auth:$ha2")
            connexion = ouvrir(
                url, methode, corps,
                "Digest username=\"${c.utilisateur}\", realm=\"${p["realm"].orEmpty()}\", nonce=\"${p["nonce"].orEmpty()}\", " +
                    "uri=\"$uri\", algorithm=MD5, qop=auth, nc=$nc, cnonce=\"$cnonce\", response=\"$reponse\""
            )
        }
        val code = connexion.responseCode
        if (code != 200) {
            // Message du serveur (ex. 422 : numéro refusé) quand il y en a un.
            val detail = try {
                connexion.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            } catch (_: Exception) {
                ""
            }
            connexion.disconnect()
            throw ErreurServeur(code, detail)
        }
        val texte = connexion.inputStream.bufferedReader().use { it.readText() }
        connexion.disconnect()
        return texte
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
