# Flutter UI preview — AlKamel Online 1.22.0

This directory contains the first Flutter/Dart implementation of the new sky-blue dashboard design.

- Primary color: `#77C9F5`
- RTL Arabic layout
- Two-column action cards with warm gold icon accents
- Bottom navigation
- Navigation callbacks use `ye.alkamel.cardsender/navigation`

## Integration safety

The existing production Android app remains Kotlin-based in this branch. This Flutter screen is a UI implementation stage and must not replace the production launcher until the Android MethodChannel bridge is implemented and each destination is mapped to its existing Kotlin feature. The current logo resource also needs to be connected as a Flutter asset before production integration. This separation avoids shipping buttons that appear functional but do not reach the existing features.

To preview this Flutter screen independently, use Flutter 3.27 or newer:
```sh
cd flutter_ui
flutter pub get
flutter run
```
