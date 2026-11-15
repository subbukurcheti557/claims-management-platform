# Frontend: Angular 22 micro-frontends

| Project | Port | Role |
|---|---|---|
| `shell` | 4200 | Host: sign-in, layout, navigation, notification bell, theme. Loads a remote per role. |
| `claimant` | 4201 | Remote, exposes `./routes`: my claims, report an incident, claim detail |
| `staff` | 4202 | Remote, exposes `./routes`: work queue, claim workspace, dashboard |
| `shared` | — | `@icmp/shared`, shared as a federation singleton: API types and clients, auth, guards, UI pieces, theme |

Remote locations are in `projects/shell/public/federation.manifest.json`; federation settings are in each project's
`federation.config.mjs`.

```bash
npm ci
npm run start:all        # claimant + staff + shell dev servers (backend must be running on :8080)
npm run build:all        # production builds into dist/
npm test                 # Vitest unit tests for all projects
npx playwright test      # browser e2e against the running stack (npx playwright install chromium once)
node scripts/screenshots.mjs   # refresh docs/screenshots
```
