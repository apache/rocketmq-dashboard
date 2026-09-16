/**
 * Extracts the server-supplied message from an API error so callers can surface the
 * concrete rejection reason instead of a generic fallback.
 */
export function describeApiError(error: unknown, fallback: string): string {
  const serverMessage = (error as { response?: { data?: { message?: unknown } } })?.response?.data
    ?.message;
  return typeof serverMessage === 'string' && serverMessage.trim() ? serverMessage : fallback;
}
