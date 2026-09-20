import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import Badge from './Badge';

describe('Badge', () => {
  it('wraps a long status inside its cell instead of overflowing the card', () => {
    render(<Badge variant="warning">Conexión perdida, reintentando en 10s</Badge>);

    const badge = screen.getByText('Conexión perdida, reintentando en 10s');
    expect(badge.className).toContain('max-w-full');
    expect(badge.className).toContain('break-words');
    expect(badge.className).not.toContain('whitespace-nowrap');
  });
});
