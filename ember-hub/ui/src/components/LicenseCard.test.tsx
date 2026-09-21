import { render, screen, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect } from 'vitest';
import LicenseCard from './LicenseCard';

describe('LicenseCard', () => {
  it('shows a neutral "Sin licencia" badge when status is NONE', () => {
    render(
      <LicenseCard
        license={{ status: 'NONE', lastHeartbeatAt: null, suspendedSince: null }}
        onSelectLicense={vi.fn()}
        onRemoveLicense={vi.fn()}
      />
    );

    expect(screen.getByText('Sin licencia')).toBeTruthy();
  });

  it('shows a green "OK" badge with last-heartbeat text', () => {
    render(
      <LicenseCard
        license={{ status: 'OK', lastHeartbeatAt: new Date().toISOString(), suspendedSince: null }}
        onSelectLicense={vi.fn()}
        onRemoveLicense={vi.fn()}
      />
    );

    expect(screen.getByText('OK')).toBeTruthy();
    expect(screen.getByText(/último contacto/)).toBeTruthy();
  });

  it('shows a red "Suspendida" badge', () => {
    render(
      <LicenseCard
        license={{ status: 'SUSPENDED', lastHeartbeatAt: null, suspendedSince: new Date().toISOString() }}
        onSelectLicense={vi.fn()}
        onRemoveLicense={vi.fn()}
      />
    );

    expect(screen.getByText('Suspendida')).toBeTruthy();
  });

  it('calls onSelectLicense when the button is clicked', () => {
    const onSelectLicense = vi.fn();
    render(
      <LicenseCard
        license={{ status: 'NONE', lastHeartbeatAt: null, suspendedSince: null }}
        onSelectLicense={onSelectLicense}
        onRemoveLicense={vi.fn()}
      />
    );

    fireEvent.click(screen.getByText('Seleccionar license.key…'));

    expect(onSelectLicense).toHaveBeenCalledTimes(1);
  });

  it('hides "Eliminar license.key" when there is no license yet', () => {
    render(
      <LicenseCard
        license={{ status: 'NONE', lastHeartbeatAt: null, suspendedSince: null }}
        onSelectLicense={vi.fn()}
        onRemoveLicense={vi.fn()}
      />
    );

    expect(screen.queryByText('Eliminar license.key')).toBeNull();
  });

  it('shows "Eliminar license.key" and calls onRemoveLicense when clicked', () => {
    const onRemoveLicense = vi.fn();
    render(
      <LicenseCard
        license={{ status: 'OK', lastHeartbeatAt: new Date().toISOString(), suspendedSince: null }}
        onSelectLicense={vi.fn()}
        onRemoveLicense={onRemoveLicense}
      />
    );

    fireEvent.click(screen.getByText('Eliminar license.key'));

    expect(onRemoveLicense).toHaveBeenCalledTimes(1);
  });

  it('shows a "Migrada a Web (solo lectura)" badge and explains the read-only mode', () => {
    render(
      <LicenseCard
        license={{
          status: 'MIGRATED',
          lastHeartbeatAt: null,
          suspendedSince: null,
          migratedSince: new Date().toISOString()
        }}
        onSelectLicense={vi.fn()}
        onRemoveLicense={vi.fn()}
      />
    );

    expect(screen.getByText('Migrada a Web (solo lectura)')).toBeTruthy();
    expect(screen.getByText(/modo consulta/i)).toBeTruthy();
  });
});
