import { render, screen, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect } from 'vitest';
import LicenseCard from './LicenseCard';

describe('LicenseCard', () => {
  it('shows a neutral "Sin licencia" badge when status is NONE', () => {
    render(<LicenseCard license={{ status: 'NONE', lastHeartbeatAt: null, suspendedSince: null }} onSelectLicense={vi.fn()} />);

    expect(screen.getByText('Sin licencia')).toBeTruthy();
  });

  it('shows a green "OK" badge with last-heartbeat text', () => {
    render(<LicenseCard license={{ status: 'OK', lastHeartbeatAt: new Date().toISOString(), suspendedSince: null }} onSelectLicense={vi.fn()} />);

    expect(screen.getByText('OK')).toBeTruthy();
    expect(screen.getByText(/último contacto/)).toBeTruthy();
  });

  it('shows a red "Suspendida" badge', () => {
    render(<LicenseCard license={{ status: 'SUSPENDED', lastHeartbeatAt: null, suspendedSince: new Date().toISOString() }} onSelectLicense={vi.fn()} />);

    expect(screen.getByText('Suspendida')).toBeTruthy();
  });

  it('calls onSelectLicense when the button is clicked', () => {
    const onSelectLicense = vi.fn();
    render(<LicenseCard license={{ status: 'NONE', lastHeartbeatAt: null, suspendedSince: null }} onSelectLicense={onSelectLicense} />);

    fireEvent.click(screen.getByText('Seleccionar license.key…'));

    expect(onSelectLicense).toHaveBeenCalledTimes(1);
  });
});
