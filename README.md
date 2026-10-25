# DevxyzFCS — AIDE FCS-like IDE (AndroidX/AppCompat)

Reference lang ang sinend na AIDE FCS APK at TriNet files: ginamit sila para
suriin kung ano ang IDE at para patunayan na gumagana ang pag-build ng
AndroidX/AppCompat. Hindi kinopya ang code, logo, o assets ng AIDE.

## Ano ito (v1, totoong handlers)
- Project list: gumawa ng bagong AndroidX AppCompat project mula sa template.
- Editor: pumili ng file, mag-edit, mag-save (totoong file I/O sa app storage).
- Build Check: binabasa ang totoong project files at chine-check ang
  AndroidX/AppCompat conditions (MainActivity, Manifest, layout, AppCompat
  import, extends AppCompatActivity, appcompat dependency).
- Sample module (:sample): kaparehong template ng IDE, na bina-build ng CI
  para patunayan na nagko-compile ang AndroidX/AppCompat.

## Honest limit (v1)
Ang full Gradle build ay HINDI tumatakbo sa loob mismo ng phone — kailangan
ng JDK + Android SDK + Gradle daemon, na wala sa phone. Kaya ang patunay
ng build ay ang GitHub Actions CI job na nagbi-build ng :sample at :app.
Ang on-device Build Check ay structural validation, hindi Gradle compile.

## Build (CI)
- `:app:assembleDebug` — ang IDE app mismo (DevxyzFCS).
- `:sample:assembleDebug` — ang AndroidX AppCompat template project.
