# JE SUIS KCCiste – FORMATION (projet Android)

L'application web (`app/src/main/assets/index.html`) est affichée dans une WebView native.
Elle fonctionne hors ligne et ne demande aucune permission.

## Option A – Sans ordinateur puissant : GitHub (gratuit)
1. Créez un compte sur github.com puis un nouveau dépôt (Public ou Privé).
2. « Add file → Upload files » : déposez TOUT le contenu de ce dossier (y compris le dossier `.github`).
   Si le dossier `.github` n'est pas envoyé : « Add file → Create new file »,
   nommez-le `.github/workflows/build-apk.yml` et collez le contenu du fichier fourni.
3. Onglet « Actions » : la construction démarre (3 à 6 minutes).
4. Ouvrez l'exécution terminée (✓ vert) → section « Artifacts » → téléchargez `KCCiste-Formation-APK`.
5. Décompressez : vous obtenez `KCCiste-Formation.apk`.

## Option B – Avec Android Studio (ordinateur)
1. Installez Android Studio, puis « Open » → choisissez ce dossier.
2. Attendez la synchronisation Gradle.
3. Menu « Build → Build App Bundle(s) / APK(s) → Build APK(s) ».
4. L'APK se trouve dans `app/build/outputs/apk/debug/`.

## Installer sur un téléphone
Envoyez l'APK sur le téléphone (WhatsApp, câble, Drive), ouvrez-le et autorisez
« Installer des applications inconnues » quand Android le demande.

## Mettre à jour l'application
1. Remplacez `app/src/main/assets/index.html` par la nouvelle version.
2. Dans `app/build.gradle`, augmentez `versionCode` (1 → 2 → 3…) et `versionName`.
3. Reconstruisez l'APK. La progression des utilisateurs est conservée.

## Publication sur Google Play (plus tard)
Il faut une version « release » signée avec votre propre clé :
Android Studio → « Build → Generate Signed App Bundle / APK ». Conservez précieusement la clé.

## Version iPhone (sans App Store)
La même application est publiée comme site web installable grâce à GitHub Pages
(fichier `.github/workflows/pages.yml`, dossier `web/` : manifeste, icônes, mode hors ligne).
1. Une seule fois : dépôt **public**, puis « Settings → Pages → Source : GitHub Actions ».
2. À chaque modification de `index.html` sur `main`, le site est republié automatiquement.
3. Sur l'iPhone : ouvrez le lien dans **Safari**, touchez **Partager** ⬆️ puis
   **« Sur l'écran d'accueil »**. L'icône apparaît comme une application et fonctionne hors ligne.

## Version iPhone pour l'App Store (.ipa)
Le dossier `ios/` contient l'application iPhone (même `index.html`, affiché dans une WKWebView,
avec enregistrement dans Photos, partage et impression du certificat).
Le fichier `.github/workflows/build-ios.yml` la fabrique sur un Mac de GitHub : pas besoin de Mac.
1. Compte Apple Developer (99 $/an) et fiche de l'application dans App Store Connect
   (identifiant du paquet : `com.jesuiskcciste.formation`).
2. Clé API App Store Connect (rôle « Admin »), puis 4 secrets du dépôt :
   `APPLE_TEAM_ID`, `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8` (contenu du fichier .p8).
3. Onglet « Actions » › « Construire l'application iPhone (.ipa) » › « Run workflow » :
   le `.ipa` signé est dans « Artifacts » et il est envoyé dans TestFlight.
4. Politique de confidentialité à indiquer à Apple :
   https://reaganmbumba.github.io/je-suis-kcciste-formation/confidentialite.html
