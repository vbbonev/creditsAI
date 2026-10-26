# Frontend conventions

Angular with **Angular Material** (Material Design 3) and **template-driven forms**. Requires Node 20+ (`nvm use 20`).

## UI: Angular Material only
- Build every UI element from Angular Material components: `mat-toolbar`, `mat-sidenav`, `mat-card`, `mat-form-field` + `matInput`, `mat-select`, `mat-datepicker`, `mat-button`, `mat-icon`, `mat-table`, `mat-paginator`, `mat-sort`, `mat-dialog`, `mat-snack-bar`.
- Don't add other UI libraries (no Bootstrap, PrimeNG, Tailwind components). Use custom CSS only for layout and spacing.
- One Material theme, defined in `src/styles.scss` with `mat.theme(...)`. Don't hardcode colors in components; use the theme's CSS variables.

## Forms: template-driven only
- Use `FormsModule` with `[(ngModel)]` and a `name` on every control. The form is accessed through `#form="ngForm"`.
- **Never** use `ReactiveFormsModule`, `FormBuilder`, `FormGroup`, `FormControl` or `FormArray`. A hook blocks them.
- Validation is in the template and mirrors the backend DTO constraints: `required`, `minlength`, `maxlength`, `min`, `max`, `pattern`, `email`. Rules the built-in validators can't express go into a custom validator **directive** (implementing `Validator`, registered with `NG_VALIDATORS`).
- Show errors with `<mat-error>` inside `mat-form-field`, one message per failing validator. Disable submit while `form.invalid`.
- Validation runs twice: the frontend validation is for user experience, and the backend has the final say. When the backend returns 400 with field errors, show them on the matching controls. Show any other error's `detail` message in a `MatSnackBar`.

## Mapping to the backend
- Paginated lists: `mat-table` + `mat-paginator` + `matSort`. They map to `page`, `size` and `sort=field,dir` query params and read Spring's `Page` response (`content`, `page.totalElements`).
- Role-aware UI: edit and archive actions are shown only to `ADMIN` (checked with `isAdmin()`, see Authentication). The backend still enforces this, and the UI never replaces it.
- "Delete" means archive. Always ask for confirmation in a `MatDialog` first, and show the backend's message if archiving is refused (409, e.g. the client has an active credit).

## Structure: by feature
```
src/app/
├── core/          app-wide singletons: auth (Keycloak setup, role helpers), HTTP error interceptor, layout (toolbar, sidenav)
├── shared/        reusable pieces: validator directives, confirm dialog, page/sort models, pipes
└── features/
    ├── clients/   client-list, client-form, client-detail components + client.service.ts + client.model.ts
    └── credits/   credit components + credit.service.ts + credit.model.ts
```
- A feature folder holds everything for that feature: components, a service that calls the API, and model interfaces.
- Features never import from each other. Anything they share moves to `shared/`.
- Standalone components only. Routes are lazy-loaded per feature (`loadChildren` → `features/<name>/<name>.routes.ts`).

## Authentication: keycloak-angular
- Use the **functional API** of `keycloak-angular`:
  - `provideKeycloak(...)` in `app.config.ts`, with `url`, `realm` and `clientId` taken from `environment`, and `initOptions: { onLoad: 'login-required', pkceMethod: 'S256' }`. The whole app requires login.
  - The bearer-token interceptor, configured to attach the token **only** to requests that start with `environment.apiUrl`.
  - One environment, because the app always runs in Docker behind nginx: `apiUrl: '/api'` (same origin, no CORS), Keycloak `url: 'http://localhost/auth'`. See "One mode" in `.claude/rules/infrastructure.md`.
  - `createAuthGuard` for role-protected routes (ADMIN-only routes such as edit).
- Check roles in one place, a `core/auth` service with `isAdmin()` (realm role `ADMIN`).
- Never store tokens yourself (no localStorage). keycloak-angular manages and refreshes them.

## JSDoc
- **Every method** gets a JSDoc comment, including private methods, lifecycle hooks and service methods:
  - one sentence on what it does
  - `@param` for each parameter
  - `@returns` for non-void methods (e.g. "Observable emitting the created client")
  - `@throws` if it throws
- Every component, service, directive and interface gets a class/interface-level JSDoc. Name the design pattern if one is used.
- Spec files are documented by their `describe`/`it` texts.

## Tests
- Every component and service gets a `*.spec.ts`.

## Language
- All UI texts, labels, validation messages and snackbar messages are in **English**.
