/**
 * Production build configuration.
 *
 * `apiBaseUrl` is the Spring Boot backend origin. Override it at build time to
 * point the SPA at a deployed API (e.g. via a CI substitution or a value
 * injected before `ng build`). The dev configuration overrides this file with
 * `ng build --configuration development` / `ng serve`.
 */
export const environment = {
  production: true,
  apiBaseUrl: 'http://localhost:8080',
};
