/*
 * Compile-time configuration.
 */

/**
 * @deprecated Use `isMockMode()` from `./services/dataMode` instead.
 * The data mode is now a runtime toggle persisted in localStorage via Zustand.
 * This export is kept temporarily for backward compatibility and will be removed.
 */
export const USE_MOCK = false;

/** API prefix for browser requests. Defaults to the reverse-proxy friendly `/api`. */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
