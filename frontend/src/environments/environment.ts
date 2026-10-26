/**
 * The only environment. The app always runs in Docker behind nginx on http://localhost,
 * so the API is same-origin (`/api`) and Keycloak is served under `/auth`.
 */
export const environment = {
  apiUrl: '/api',
  keycloak: {
    url: 'http://localhost/auth',
    realm: 'credits',
    clientId: 'credits-web',
  },
};
