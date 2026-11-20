# Frontend: Angular 22 single-page application

One Angular application on port 4200. The claimant and staff areas are lazily loaded route chunks, so a user only
downloads the code for the area their role allows.

```
src/
├── main.ts, index.html, styles.scss, styles/_theme.scss   entry point and the design system
└── app/
    ├── app.routes.ts      /login, /claimant/** (CLAIMANT), /staff/** (CLAIMS_OFFICER, CLAIMS_MANAGER)
    ├── layout/, login/, notifications/, theme/, pages/     app frame: top bar, nav, bell, theme toggle, 404
    ├── claimant/          my claims, report an incident, claim detail
    ├── staff/             work queue, claim workspace, dashboard
    └── shared/            imported as `@icmp/shared` (tsconfig path): API types and clients, auth, guards, UI pieces
```

```bash
npm ci
npm start                # dev server on http://localhost:4200 with live reload (backend must be running on :8080)
npm run build            # production build into dist/claims-hub/browser
npm test                 # Vitest unit tests
npx playwright test      # browser e2e against the running stack (npx playwright install chromium once)
node scripts/screenshots.mjs   # refresh docs/screenshots
```
