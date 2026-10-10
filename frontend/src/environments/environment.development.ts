/**
 * Development build configuration (used by `ng serve` and
 * `ng build --configuration development`).
 *
 * Points the Angular dev server at the local Spring Boot API. Start the
 * backend on port 8080 (see the root README) before signing in.
 */
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080',
};
