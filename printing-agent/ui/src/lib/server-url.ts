export const DEFAULT_SERVER = 'https://api.ember.vanter.net/v1';

/**
 * Cleans what an operator typed into the "Servidor" field: trims, adds `http://` when the scheme
 * is missing (a LAN Hub is usually typed as `192.168.1.10:8080`) and drops trailing slashes.
 * Returns '' for a blank value so the caller can refuse to submit.
 */
export function normalizeServerUrl(raw: string): string {
  const value = raw.trim();
  if (!value) return '';
  const withScheme = /^https?:\/\//i.test(value) ? value : `http://${value}`;
  return withScheme.replace(/\/+$/, '');
}
