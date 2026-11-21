/*
 * DzCentrex : modification de Linphone Android (GPLv3), voir LISEZMOI-DZCENTREX.md.
 *
 * Écran Renvois du menu : ne pas déranger, renvois (toujours, si occupé, si pas de réponse) vers un numéro ou un
 * collègue de l'annuaire, messagerie vocale (si le client a l'offre) et horaires d'ouverture. Mêmes réglages que dans
 * l'espace utilisateur du portail (RenvoisDz).
 */
package org.linphone.dzcentrex

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.contacts.AnnuaireDz
import org.linphone.core.tools.Log
import org.linphone.databinding.DzRenvoisFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.fragment.GenericMainFragment

@UiThread
class RenvoisFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[DzCentrex Renvois]"
    }

    private lateinit var binding: DzRenvoisFragmentBinding

    private var compte: RenvoisDz.Compte? = null

    // Dernier état lu sur le serveur : les champs que l'écran ne montre pas sont renvoyés tels quels.
    private var actuel = JSONObject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DzRenvoisFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = viewLifecycleOwner

        binding.setBackClickListener {
            goBack()
        }

        binding.toujoursCollegue.setOnClickListener { choisirCollegue(binding.toujours) }
        binding.occupeCollegue.setOnClickListener { choisirCollegue(binding.occupe) }
        binding.nonReponseCollegue.setOnClickListener { choisirCollegue(binding.nonReponse) }
        binding.enregistrer.setOnClickListener { enregistrer() }

        coreContext.postOnCoreThread { core ->
            val c = RenvoisDz.compte(core)
            coreContext.postOnMainThread {
                if (!isAdded) return@postOnMainThread
                compte = c
                if (c == null) {
                    afficherEtat(getString(R.string.dz_renvois_pas_configure))
                } else {
                    charger()
                }
            }
        }
    }

    private fun charger() {
        val c = compte ?: return
        afficherEtat(getString(R.string.dz_renvois_chargement))
        Thread {
            val resultat = runCatching { RenvoisDz.lire(c) }
            coreContext.postOnMainThread {
                if (!isAdded) return@postOnMainThread
                resultat.onSuccess { afficher(it) }.onFailure {
                    Log.e("$TAG Lecture impossible : $it")
                    afficherEtat(getString(R.string.dz_renvois_erreur_lecture))
                }
            }
        }.start()
    }

    private fun afficherEtat(texte: String) {
        binding.etat.text = texte
        binding.etat.visibility = View.VISIBLE
    }

    private fun texte(r: JSONObject, cle: String): String = if (r.isNull(cle)) "" else r.optString(cle)

    private fun afficher(r: JSONObject) {
        actuel = r
        binding.etat.visibility = View.GONE
        binding.formulaire.visibility = View.VISIBLE
        binding.enregistrer.visibility = View.VISIBLE
        binding.nePasDeranger.isChecked = r.optBoolean("ne_pas_deranger")
        binding.toujours.setText(texte(r, "toujours"))
        binding.occupe.setText(texte(r, "occupe"))
        binding.nonReponse.setText(texte(r, "non_reponse"))
        binding.delai.setText(r.optInt("delai", 20).toString())
        binding.ligneMessagerie.visibility = if (r.optBoolean("messagerie_offre")) View.VISIBLE else View.GONE
        binding.messagerie.isChecked = r.optBoolean("messagerie", true)
        // Case cochée : le poste suit les horaires d'ouverture (comme dans le portail).
        binding.horaires.isChecked = !r.optBoolean("hors_horaires")
    }

    private fun numero(champ: EditText): Any {
        val v = champ.text.toString().replace(" ", "").replace(".", "").replace("-", "")
        return v.ifEmpty { JSONObject.NULL }
    }

    private fun enregistrer() {
        val c = compte ?: return
        val delai = binding.delai.text.toString().toIntOrNull()
        if (delai == null || delai !in 5..60) {
            (requireActivity() as GenericActivity).showRedToast(
                getString(R.string.dz_renvois_delai_invalide),
                R.drawable.warning_circle
            )
            return
        }
        val r = JSONObject()
        r.put("toujours", numero(binding.toujours))
        r.put("occupe", numero(binding.occupe))
        r.put("non_reponse", numero(binding.nonReponse))
        r.put("delai", delai)
        r.put("ne_pas_deranger", binding.nePasDeranger.isChecked)
        r.put("messagerie", if (actuel.optBoolean("messagerie_offre")) binding.messagerie.isChecked else JSONObject.NULL)
        r.put("hors_horaires", !binding.horaires.isChecked)
        binding.enregistrer.isEnabled = false
        Thread {
            val resultat = runCatching { RenvoisDz.enregistrer(c, r) }
            coreContext.postOnMainThread {
                if (!isAdded) return@postOnMainThread
                binding.enregistrer.isEnabled = true
                val activite = requireActivity() as GenericActivity
                resultat.onSuccess {
                    afficher(it)
                    activite.showGreenToast(getString(R.string.dz_renvois_enregistre), R.drawable.check)
                }.onFailure {
                    Log.e("$TAG Enregistrement impossible : $it")
                    val message = if (it is RenvoisDz.ErreurServeur && it.code == 422) {
                        R.string.dz_renvois_numero_refuse
                    } else {
                        R.string.dz_renvois_erreur_enregistrement
                    }
                    activite.showRedToast(getString(message), R.drawable.warning_circle)
                }
            }
        }.start()
    }

    /** Collègues de l'annuaire DzCentrex (onglet Entreprise) : le champ reçoit leur numéro de poste. */
    private fun choisirCollegue(champ: EditText) {
        coreContext.postOnCoreThread { core ->
            val collegues = core.getFriendListByName(AnnuaireDz.NOM_LISTE)?.friends.orEmpty()
                .mapNotNull { f -> f.address?.username?.let { (f.name ?: it) to it } }
                .sortedBy { it.second }
            coreContext.postOnMainThread {
                if (!isAdded) return@postOnMainThread
                if (collegues.isEmpty()) {
                    (requireActivity() as GenericActivity).showRedToast(
                        getString(R.string.dz_renvois_aucun_collegue),
                        R.drawable.warning_circle
                    )
                    return@postOnMainThread
                }
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.dz_renvois_choisir_collegue)
                    .setItems(collegues.map<Pair<String, String>, CharSequence> { "${it.first} (${it.second})" }.toTypedArray()) { _, i ->
                        champ.setText(collegues[i].second)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }
}
