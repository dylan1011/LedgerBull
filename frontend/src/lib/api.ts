/**
 * Central place for the LedgerBull backend API configuration.
 *
 * The value comes from the `NEXT_PUBLIC_API_BASE_URL` environment variable so it
 * can differ per environment. It must be `NEXT_PUBLIC_`-prefixed to be readable
 * in the browser. Falls back to the local API Gateway when unset.
 */
export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
