/*
 * DzCentrex : état des collègues (onglet Entreprise), envoyé par le serveur.
 *
 * Tant que l'appli est au premier plan, une seule connexion HTTPS (/prov/etat, à côté de l'annuaire, compte SIP du
 * poste en Digest) reçoit l'état de tous les postes du client, puis seulement les changements. Remplace les
 * abonnements SIP « presence » : un par collègue faisait des rafales qui bloquaient l'IP de tout un site, et la liste
 * d'état SIP (RLS) faisait planter l'appli (test14). Fermée en arrière-plan ; au retour, l'état complet est renvoyé.
 *
 * Code ajouté à Linphone Android (GPLv3).
 */
package org.linphone.contacts

import androidx.annotation.WorkerThread
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONObject
import org.linphone.core.ConsolidatedPresence
import org.linphone.core.Core
import org.linphone.core.tools.Log
import org.linphone.dzcentrex.RenvoisDz

object EtatDz {
    private const val TAG = "[État DzCentrex]"

    // Le serveur envoie un commentaire toutes les 20 s : sans rien pendant 60 s, la connexion est morte.
    private const val LECTURE_MAX = 60000

    private val etats = ConcurrentHashMap<String, String>()
    private val ecouteurs = CopyOnWriteArrayList<(String) -> Unit>()

    @Volatile var premierPlan = false

    @Volatile private var fil: Thread? = null

    // Chaque démarrage a son numéro : un fil d'un démarrage précédent (arrière-plan puis retour rapide) s'arrête seul.
    @Volatile private var generation = 0

    @Volatile private var connexion: HttpURLConnection? = null

    /** disponible, sonne, en_appel, ne_pas_deranger, hors_ligne, inconnu ; null : pas encore reçu. */
    fun etat(extension: String): String? = etats[extension]

    fun presence(extension: String): ConsolidatedPresence = when (etats[extension]) {
        "disponible" -> ConsolidatedPresence.Online
        "sonne", "en_appel" -> ConsolidatedPresence.Busy
        "ne_pas_deranger" -> ConsolidatedPresence.DoNotDisturb
        else -> ConsolidatedPresence.Offline
    }

    /** Appelé avec le numéro du poste dont l'état a changé (fil de l'état, pas le fil du Core). */
    fun ecouter(ecouteur: (String) -> Unit) {
        ecouteurs.add(ecouteur)
    }

    fun oublier(ecouteur: (String) -> Unit) {
        ecouteurs.remove(ecouteur)
    }

    @WorkerThread
    fun demarrer(core: Core) {
        if (!premierPlan || fil?.isAlive == true) return
        val compte = RenvoisDz.compte(core, "etat") ?: return
        Log.i("$TAG Connexion à ${compte.adresse}")
        val g = ++generation
        fil = Thread({ boucle(compte, g) }, "EtatDz").also { it.start() }
    }

    fun arreter() {
        premierPlan = false
        generation++
        fil?.interrupt()
        fil = null
        connexion?.disconnect()
    }

    private fun boucle(compte: RenvoisDz.Compte, g: Int) {
        var attente = 2000L
        while (generation == g) {
            var k: HttpURLConnection? = null
            try {
                k = RenvoisDz.connecter(compte, "GET", null, LECTURE_MAX)
                connexion = k
                val code = k.responseCode
                if (code != 200) throw IllegalStateException("HTTP $code")
                attente = 2000L
                val lecteur = k.inputStream.bufferedReader()
                while (generation == g) {
                    val ligne = lecteur.readLine() ?: break
                    if (ligne.startsWith("data:")) appliquer(JSONObject(ligne.removePrefix("data:").trim()))
                }
            } catch (e: Exception) {
                if (generation == g) Log.w("$TAG Connexion perdue : $e")
            } finally {
                k?.disconnect()
                if (connexion === k) connexion = null
            }
            if (generation != g) break
            try {
                Thread.sleep(attente)
            } catch (_: InterruptedException) {
                break
            }
            attente = (attente * 2).coerceAtMost(60000L)
        }
        Log.i("$TAG Connexion fermée")
    }

    private fun appliquer(changes: JSONObject) {
        for (extension in changes.keys()) {
            val etat = changes.getString(extension)
            if (etats.put(extension, etat) != etat) {
                for (e in ecouteurs) e(extension)
            }
        }
    }
}
