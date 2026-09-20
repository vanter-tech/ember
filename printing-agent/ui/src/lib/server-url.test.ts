import { describe, it, expect } from 'vitest';
import { normalizeServerUrl } from './server-url';

describe('normalizeServerUrl', () => {
  it('adds http:// to a bare LAN address', () => {
    expect(normalizeServerUrl('192.168.1.10:8080')).toBe('http://192.168.1.10:8080');
  });

  it('keeps an explicit scheme and drops trailing slashes and whitespace', () => {
    expect(normalizeServerUrl('  https://api.ember.vanter.net/v1/  ')).toBe('https://api.ember.vanter.net/v1');
    expect(normalizeServerUrl('http://localhost:8080//')).toBe('http://localhost:8080');
  });

  it('returns an empty string for a blank value', () => {
    expect(normalizeServerUrl('   ')).toBe('');
  });
});
