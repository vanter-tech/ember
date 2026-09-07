// True only in the Hub-bundled SPA (`vite build --base=/app/`, see ember-hub/build-frontend.ps1).
// Cloud and dev builds have BASE_URL "/". Same signal App.tsx uses for the router basename.
// In the Hub build there is no customer-facing flow: the waiter drives the whole table.
export const isHubBuild = import.meta.env.BASE_URL !== '/'
