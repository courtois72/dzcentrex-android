/*
 * DzCentrex : réception des notifications push. Celles du serveur DzCentrex marquées « dz » (ex. annuaire modifié :
 * un poste créé, renommé ou supprimé) sont traitées ici, sans rien afficher ; toutes les autres (réveil avant un
 * appel) vont au service du SDK Linphone, comme avant.
 *
 * Code ajouté à Linphone Android (GPLv3).
 */
package org.linphone.contacts

import com.google.firebase.messaging.RemoteMessage
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.GlobalState
import org.linphone.core.tools.Log
import org.linphone.core.tools.firebase.FirebaseMessaging

class PushDz : FirebaseMessaging() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data["dz"] == "annuaire") {
            Log.i("[Push DzCentrex] Annuaire modifié, rechargement")
            // Moteur arrêté : il démarre avec l'application et charge l'annuaire à son démarrage (onCoreStarted).
            coreContext.postOnCoreThread { core ->
                if (core.globalState == GlobalState.On) {
                    AnnuaireDz.charger(core)
                }
            }
            return
        }
        super.onMessageReceived(remoteMessage)
    }
}
