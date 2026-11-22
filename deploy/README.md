# Local deployment

The walking skeleton runs the app and PostgreSQL through Docker Compose. PostgreSQL is on the internal network and only the app port (8080) is published. Persistent database data uses the \`pgdata\` named volume.

From the repository root, using Bash (Git Bash or WSL on Windows):

\`\`\`bash
./deploy/scripts/init-env.sh
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.build.yml --env-file deploy/.env up --build -d
\`\`\`

Open \`http://localhost:8080\` and create the first account. That account becomes the instance administrator. Later accounts register with a campaign invite.

View logs:

\`\`\`bash
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.build.yml --env-file deploy/.env logs -f app db
\`\`\`

Stop without deleting database data:

\`\`\`bash
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.build.yml --env-file deploy/.env down
\`\`\`

Use \`down -v\` only when you explicitly intend to delete the local database volume. Keep \`deploy/.env\` private and out of Git. Re-run the initialization script on an existing installation to add the instance signing key while keeping the database password. Keep the key stable so invites and form submissions remain valid across restarts. Demo accounts created with BCrypt need fresh Argon2id credentials.
