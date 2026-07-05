# Build: Java 21 richiesto

Questo progetto richiede un toolchain **Java 21** per compilare `buildSrc` e avviare Gradle.

Se durante `./gradlew assembleDebug` compare un errore simile a:

```text
Cannot find a Java installation on your machine matching: {languageVersion=21}
Toolchain download repositories have not been configured.
```

questa versione configura il resolver Gradle/Foojay sia nel progetto principale sia in `buildSrc`, così Gradle può scaricare automaticamente un JDK 21 compatibile quando la macchina non lo ha già installato.

## Soluzione consigliata

Riprova:

```bash
./gradlew --stop
./gradlew assembleDebug
```

## Alternativa manuale

Se preferisci non usare il download automatico del toolchain, installa Java 21 sul sistema e poi rilancia la build.

Su Ubuntu/Debian, una delle opzioni più semplici è usare Temurin 21 o OpenJDK 21, in base ai repository disponibili sulla tua distribuzione.

Verifica con:

```bash
java -version
./gradlew -version
```

## Nota

Il progetto può comunque compilare codice Android con target Java 8 dove previsto; il requisito Java 21 riguarda il toolchain usato da Gradle/`buildSrc`, non significa che l'app richieda Android con Java 21.
