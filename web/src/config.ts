/*
 * Compile-time configuration.
 * VITE_USE_MOCK controls whether the app uses mock data or real API calls.
 * Set via .env file or build-time environment variable.
 */

export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

/** API prefix for browser requests. Defaults to the reverse-proxy friendly `/api`. */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
