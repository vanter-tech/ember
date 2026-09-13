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

  it('renders no empty chip on ERROR when the caller passes a null error (license-blocked case)', () => {
    const { container } = render(
      <ServiceCard id="postgres" icon={Database} title="PostgreSQL" phase="ERROR" error={null} />
    );

    expect(screen.getByText('Error')).toBeTruthy();
    expect(container.querySelector('.mt-3')).toBeNull();
  });

  it('mounting directly at RUNNING has no log to show yet', () => {
    render(<ServiceCard id="postgres" icon={Database} title="PostgreSQL" phase="RUNNING" error={null} />);

    expect(screen.getByText('En ejecución')).toBeTruthy();
    expect(screen.queryByText(/database system is ready/)).toBeNull();
  });

  describe('log persistence and manual toggle', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('keeps showing the log after the phase moves past STARTING (does not auto-clear)', () => {
      const { rerender } = render(
        <ServiceCard id="postgres" icon={Database} title="PostgreSQL" phase="STARTING" error={null} />
      );
      act(() => {
        vi.advanceTimersByTime(3 * 350);
      });
      expect(screen.getByText(/database system is ready/)).toBeTruthy();

      rerender(<ServiceCard id="postgres" icon={Database} title="PostgreSQL" phase="RUNNING" error={null} />);
      expect(screen.getByText(/database system is ready/)).toBeTruthy();
    });

    it('can be manually collapsed and re-expanded via the toggle button', () => {
      render(
        <ServiceCard id="postgres" icon={Database} title="PostgreSQL" phase="ERROR" error="El puerto 5432 ya está en uso." />
      );
      expect(screen.getByText('El puerto 5432 ya está en uso.')).toBeTruthy();

      const toggle = screen.getByRole('button');
      act(() => toggle.click());
      expect(screen.queryByText('El puerto 5432 ya está en uso.')).toBeNull();

      act(() => toggle.click());
      expect(screen.getByText('El puerto 5432 ya está en uso.')).toBeTruthy();
    });
  });
});
