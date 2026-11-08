# DzCentrex pour Android

Application de téléphonie de la plateforme DzCentrex, basée sur
[Linphone Android](https://github.com/BelledonneCommunications/linphone-android) 6.2.10
de Belledonne Communications, sous licence **GPLv3** (voir `LICENSE.txt`). Linphone est une marque de
Belledonne Communications ; cette application n'est ni produite ni soutenue par Belledonne Communications.

Modifications par rapport à Linphone (voir l'historique Git) :

- identifiant `dz.dzcentrex.telephone` et nom « DzCentrex » ;
- notifications push par le projet Firebase de DzCentrex ;
- réglages par défaut : IPv6 désactivé, service gardé actif, pas de création de compte linphone.org ni de
  réunions, pas de serveurs de fichiers, de journaux ni de vérification de version de linphone.org ;
- compilation par GitHub Actions (`.github/workflows/compiler.yml`).

Le SDK Linphone n'est pas modifié (paquets publiés par Belledonne Communications).
