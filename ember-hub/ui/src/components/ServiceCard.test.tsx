import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest';
import { Database } from 'lucide-react';
import ServiceCard from './ServiceCard';

describe('ServiceCard', () => {
  it('shows a compact "Detenido" badge when stopped', () => {
    render(<ServiceCard id="postgres" icon={Database} title="PostgreSQL" phase="STOPPED" error={null} />);

    expect(screen.getByText('Detenido')).toBeTruthy();
    expect(screen.queryByText(/database system is ready/)).toBeNull();
  });

  describe('while STARTING', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('auto-expands with a simulated log', () => {
      render(<ServiceCard id="postgres" icon={Database} title="PostgreSQL" phase="STARTING" error={null} />);

      expect(screen.getByText('Iniciando…')).toBeTruthy();
      // useTypedLog reveals one of postgres's 3 START_SCRIPTS lines every 350ms —
      // advance past all of them (real setInterval timing, not test-only speed).
      act(() => {
        vi.advanceTimersByTime(3 * 350);
      });
      expect(screen.getByText(/database system is ready/)).toBeTruthy();
    });
  });

  it('shows the real error message and stays expanded on ERROR', () => {
    render(<ServiceCard id="postgres" icon={Database} title="PostgreSQL" phase="ERROR" error="El puerto 5432 ya está en uso." />);

    expect(screen.getByText('Error')).toBeTruthy();
    expect(screen.getByText('El puerto 5432 ya está en uso.')).toBeTruthy();
    expect(screen.queryByText(/database system is ready/)).toBeNull();
  });

  it('collapses back to compact once RUNNING', () => {
    render(<ServiceCard id="postgres" icon={Database} title="PostgreSQL" phase="RUNNING" error={null} />);

    expect(screen.getByText('En ejecución')).toBeTruthy();
    expect(screen.queryByText(/database system is ready/)).toBeNull();
  });
});
