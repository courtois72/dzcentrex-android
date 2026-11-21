# DzCentrex pour Android

Application de téléphonie de la plateforme DzCentrex, basée sur
[Linphone Android](https://github.com/BelledonneCommunications/linphone-android) 6.2.10
de Belledonne Communications, sous licence **GPLv3** (voir `LICENSE.txt`). Linphone est une marque de
Belledonne Communications ; cette application n'est ni produite ni soutenue par Belledonne Communications.

Modifications par rapport à Linphone (voir l'historique Git) :

- identifiant `dzcentrex.telephone` et nom « DzCentrex » ;
- notifications push par le projet Firebase de DzCentrex ;
- réglages par défaut : IPv6 désactivé, service gardé actif, pas de création de compte linphone.org ni de
  réunions, pas de serveurs de fichiers, de journaux ni de vérification de version de linphone.org ;
- démarrage par QR code seulement (caméra ou image du téléphone, lue avec ZXing, Apache 2.0) : pas de pages de
  bienvenue, de connexion linphone.org ni de saisie manuelle d'un compte SIP ; un seul compte ; réglages du compte et
  avancés, LDAP, CardDAV, enregistrement des appels et liens d'aide de Linphone cachés ;
- couleurs « Nuit et palmier » : barre du haut en dégradé bleu nuit vers vert, boutons d'action en vert feuille,
  logo provisoire (combiné blanc sur dégradé) à la place du logo Linphone (icône, lancement, notifications) ;
- restrictions de l'interface (un seul compte, réglages cachés) dans `linphonerc_factory`, pour qu'elles
  s'appliquent aussi après une mise à jour ;
- au lancement, demande des notifications plein écran (Android 14+, écran allumé à la sonnerie), de l'exemption de l'optimisation de la batterie et, sur Xiaomi/Redmi/Poco, du
  démarrage automatique (`org.linphone.dzcentrex.ReglagesTelephone`) ;
- écran « Renvois » dans le menu (`org.linphone.dzcentrex.RenvoisFragment`) : ne pas déranger, renvois, messagerie vocale
  et horaires d'ouverture du poste, lus et enregistrés sur le serveur (`/prov/renvois`, compte SIP du poste) ;
- pas de fenêtre « Authentification requise » (mot de passe) : le poste est configuré par QR seulement ;
- numéro de la version de test (`BuildConfig.DZ_VERSION`) en bas du menu et dans Aide ; présence de l'appareil non publiée
  (`publish_presence=0`) ;
- état des collègues reçu du serveur tant que l'appli est ouverte (`/prov/etat`, `org.linphone.contacts.EtatDz`) :
  aucun abonnement SIP à leur présence (`friendlist_subscription_enabled=0`) ;
- compilation par GitHub Actions (`.github/workflows/compiler.yml`).

Le SDK Linphone n'est pas modifié (paquets publiés par Belledonne Communications).
