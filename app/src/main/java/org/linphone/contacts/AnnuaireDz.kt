/*
 * DzCentrex : annuaire des collègues (postes du même client), chargé par l'appli elle-même.
 *
 * L'adresse est celle donnée par le QR ([misc] contacts-vcard-list, ex. https://…/prov/annuaire) ; le serveur
 * demande une authentification Digest avec le compte SIP du poste (HA1 du QR). Les vCards sont rangées dans la
 * liste « DzCentrex » (onglet Entreprise des contacts), abonnée à l'état de tous les collègues en une fois (liste etat-<client>).
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

    private var dernierRafraichissement = 0L

    // Retour dans l'appli : les abonnements à l'état des collègues (10 min) ont pu expirer pendant la veille sans être
    // renouvelés, et les collègues restaient « hors ligne » (105, test12). On les redemande, au plus une fois par minute.
    @WorkerThread
    fun rafraichirAbonnements(core: Core) {
        val liste = core.getFriendListByName(NOM_LISTE) ?: return
        val maintenant = System.currentTimeMillis()
        if (maintenant - dernierRafraichissement < 60000) return
        dernierRafraichissement = maintenant
        Log.i("$TAG Retour au premier plan : abonnements redemandés")
        // updateSubscriptions() seul ne renvoie rien si aucun collègue n'a changé (voir AccountModel.setAsDefault).
        liste.isSubscriptionsEnabled = false
        liste.isSubscriptionsEnabled = true
    }

    @WorkerThread
    private fun importer(core: Core, vcards: String, essai: Int = 1) {
        // La liste par défaut de Linphone doit exister avant la nôtre : sinon la nôtre le devient, et Linphone la vide en
        // rechargeant son propre annuaire (contacts-vcard-list) quelques secondes après (test8 : collègues disparus).
        Log.i("$TAG Liste par défaut : ${core.defaultFriendList?.displayName}")
        val liste = core.getFriendListByName(NOM_LISTE) ?: core.createFriendList().also {
            it.displayName = NOM_LISTE
            it.type = FriendList.Type.Default
            core.addFriendList(it)
        }
        // Un seul abonnement pour tous les collègues : liste d'état etat-<client> du serveur (RLS, generer-dialplan.sh),
        // au lieu d'un par collègue (rafales : le serveur bloquait l'IP de tout un site, 21/11/2026). Abonnements
        // coupés pendant l'import, repris une fois la liste remplie.
        liste.isSubscriptionsEnabled = false
        val compte = core.authInfoList.firstOrNull { !it.ha1.isNullOrEmpty() }?.username
        val domaine = core.defaultAccount?.params?.identityAddress?.domain
        if (compte != null && compte.contains("-") && domaine != null) {
            liste.rlsAddress = core.interpretUrl("sip:etat-${compte.substringBeforeLast("-")}@$domaine", false)
            Log.i("$TAG Liste d'état : ${liste.rlsAddress?.asStringUriOnly()}")
        }
        for (f in liste.friends) {
            liste.removeFriend(f)
        }
        // Lecture des vCards à la main (FN, IMPP) : l'import vCard de Linphone ne donnait aucun collègue (test5, test6).
        var n = 0
        for (carte in vcards.split("BEGIN:VCARD").drop(1)) {
            val lignes = carte.lines().map { it.trim() }
            val nom = lignes.firstOrNull { it.startsWith("FN:") }?.removePrefix("FN:")
                ?.replace("\\,", ",")?.replace("\\;", ";")?.replace("\\\\", "\\")
            val sip = lignes.firstOrNull { it.startsWith("IMPP:") }?.removePrefix("IMPP:") ?: continue
            val adresse = core.interpretUrl(sip, false) ?: continue
            val ami = core.createFriend()
            ami.edit()
            ami.name = nom ?: adresse.username
            ami.address = adresse
            ami.isSubscribesEnabled = true
            ami.incSubscribePolicy = org.linphone.core.SubscribePolicy.SPDeny
            ami.starred = true
            ami.refKey = "dz-${adresse.username}"
            ami.done()
            if (liste.addFriend(ami) == FriendList.Status.OK) n++
        }
        liste.isSubscriptionsEnabled = true
        liste.updateSubscriptions()
        dernierRafraichissement = System.currentTimeMillis()
        Log.i("$TAG $n collègue(s) chargé(s)")
        coreContext.contactsManager.notifyContactsListChanged()
        // Filet de sécurité : si la liste a été vidée entre-temps, on la remplit à nouveau (deux fois au plus).
        if (n > 0 && essai < 3) {
            coreContext.postOnCoreThreadDelayed({ c ->
                val restants = c.getFriendListByName(NOM_LISTE)?.friends?.size ?: 0
                if (restants < n) {
                    Log.w("$TAG Liste vidée ($restants sur $n), nouvel import")
                    importer(c, vcards, essai + 1)
                }
            }, 10000)
        }
    }
}
