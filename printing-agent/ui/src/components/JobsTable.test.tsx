import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import JobsTable from './JobsTable';

describe('JobsTable', () => {
  it('renders a placeholder row when there are no jobs', () => {
    render(<JobsTable jobs={[]} />);
    expect(screen.getByText('Sin trabajos recientes')).toBeTruthy();
  });

  it('renders one row per job with dashes for missing fields', () => {
    render(<JobsTable jobs={[
      { at: '2026-09-12T10:00:00Z', role: 'Cocina', queue: 'COCINA-1', result: 'OK', error: null }
    ]} />);
    expect(screen.getByText('Cocina')).toBeTruthy();
    expect(screen.getByText('COCINA-1')).toBeTruthy();
    expect(screen.getByText('—')).toBeTruthy();
  });
});
