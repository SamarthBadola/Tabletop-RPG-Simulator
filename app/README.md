# Application

Spring Boot 4.1.1 application using Temurin Java 25, Maven Wrapper 3.10.0, Thymeleaf, Spring Security, PostgreSQL, and Flyway.

From the repository root, initialise the local environment and start the source-built stack using the instructions in the root README. For application-only development with a local PostgreSQL database available through the configured environment variables:

\`\`\`bash
cd app
./mvnw -B -ntp spotless:check test
./mvnw -B -ntp verify -Pintegration
\`\`\`

The \`integration\` profile uses Testcontainers and therefore requires a working Docker daemon. The first-account registration transaction locks a singleton initialization row; only the first user is created as the instance administrator. Additional registrations require campaign invites. For application-only startup, supply APP_INSTANCE_KEY as a stable 32-byte key encoded in 64 hexadecimal characters. Docker Compose obtains it from the environment initializer.

Keep any future rules engine under a pure-JDK \`engine\` package. It must not import Spring, JPA, HTTP, or filesystem I/O.

## Fighter characters

From a campaign, open **Characters** to create or view a Fighter. Use the class equipment choices or roll starting gold. Choose from the items in the bundled pack and equip them on the sheet. The DM grants levels through level 5, and the player selects Champion at level 3 and a feat or ability improvement at level 4.

## Content packs

At startup the app validates and seeds SRD 5.1 pack version `0.2.0`, which contains Prone and the Fighter creation and leveling content. New campaigns enable `0.2.0`. A campaign on `0.1.0` keeps it until its DM reviews and confirms the upgrade in **Content packs**. Startup fails if the same version is already stored with a different hash. Each campaign reads an immutable definition set keyed by its ordered pack hashes.

The schema files and generated vocabulary are in `src/main/resources/content-schema/`. To regenerate the vocabulary after editing a registry, run this from the application directory:

```bash
./mvnw -B -ntp -Dtest=VocabularyTest -Dcontent.writeVocabulary=true test
```

The seed source index records the Creative Commons PDF hash and each bundled text excerpt's source span. To check the seed wording against the PDF, run `python deploy/scripts/verify_srd_seed.py /path/to/SRD_CC_v5.1.pdf` from the repository root with `pypdf` installed. The attribution is available at `/help` after sign-in.
