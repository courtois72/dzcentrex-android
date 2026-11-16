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
- couleurs « Nuit et palmier » (bleu nuit, vert feuille) à la place de l'orange de Linphone ;
- au lancement, demande de l'exemption de l'optimisation de la batterie et, sur Xiaomi/Redmi/Poco, du
  démarrage automatique (`org.linphone.dzcentrex.ReglagesTelephone`) ;
- compilation par GitHub Actions (`.github/workflows/compiler.yml`).

Le SDK Linphone n'est pas modifié (paquets publiés par Belledonne Communications).
