/**
 * Extracts the server-supplied message from an API error so callers can surface the
 * concrete rejection reason instead of a generic fallback.
 */
export function describeApiError(error: unknown, fallback: string): string {
  const serverMessage = (error as { response?: { data?: { message?: unknown } } })?.response?.data
    ?.message;
  return typeof serverMessage === 'string' && serverMessage.trim() ? serverMessage : fallback;
}

/**
 * Best available machine-supplied message for anything thrown, or `''` when there is none.
 *
 * Wider than {@link describeApiError} on purpose: the AI run streams are opened with `fetch`, so
 * their `AiStreamError` carries no axios response and only its own `message`. Hooks return this
 * string verbatim and leave the empty case to the component, which pairs it with an i18n fallback —
 * no user-facing text is hard-coded in a hook.
 */
export function describeThrownMessage(error: unknown): string {
  const serverMessage = describeApiError(error, '');
  if (serverMessage) return serverMessage;
  if (error instanceof Error && error.message.trim()) return error.message;
  // Not everything thrown is an Error: a rejected promise can carry a bare object, and the
  // page-level extractors this replaced accepted any string `message`.
  const thrownMessage = (error as { message?: unknown } | null | undefined)?.message;
  if (typeof thrownMessage === 'string' && thrownMessage.trim()) return thrownMessage;
  return '';
}
