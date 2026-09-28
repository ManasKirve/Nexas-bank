/**
 * Local demo build — no backend.
 *
 * `apiBaseUrl` was removed together with every `HttpClient` call: the data now
 * lives in LocalStorage via `LocalStorageService`, so there is no server address
 * to configure. The Spring Boot service in `backend/` is kept as the enterprise
 * reference architecture and is not used by this build.
 */
export const environment = {
  production: true,
};
