/*
 * Compile-time configuration.
 */

/**
 * @deprecated Use `isMockMode()` from `./services/dataMode` instead.
 * The data mode is now a runtime toggle persisted in localStorage via Zustand.
 * This export is kept temporarily for backward compatibility and will be removed.
 */
export const USE_MOCK = false;

/**
 * Normalizes the optional API endpoint supplied at build time.
 *
 * An empty result intentionally represents the same-origin root: API callers use
 * absolute paths such as `/auth/status`, so configuring `/` must not introduce a
 * double slash. Blank overrides fall back to the reverse-proxy-friendly `/api`.
 */
export function normalizeApiBaseUrl(value?: string): string {
  const configured = value?.trim() || '/api';
  return configured.replace(/\/+$/, '');
}

/** API prefix for browser requests. Defaults to the reverse-proxy friendly `/api`. */
export const API_BASE_URL = normalizeApiBaseUrl(import.meta.env.VITE_API_BASE_URL);
