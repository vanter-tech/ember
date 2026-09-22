import { render, screen, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import FirstRunCredentialsModal from './FirstRunCredentialsModal';

describe('FirstRunCredentialsModal', () => {
  beforeEach(() => {
    Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } });
  });

  it('shows the email and the generated password', () => {
    render(
      <FirstRunCredentialsModal
        email="owner@tenant-grill.local"
        password="s3cr3t-temp-pw"
        onAcknowledge={vi.fn()}
      />
    );

    expect(screen.getByText('owner@tenant-grill.local')).toBeTruthy();
    expect(screen.getByText('s3cr3t-temp-pw')).toBeTruthy();
  });

  it('copies the password to the clipboard', async () => {
    render(
      <FirstRunCredentialsModal
        email="owner@tenant-grill.local"
        password="s3cr3t-temp-pw"
        onAcknowledge={vi.fn()}
      />
    );

    fireEvent.click(screen.getByText('Copiar'));

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('s3cr3t-temp-pw');
    expect(await screen.findByText('Copiada')).toBeTruthy();
  });

  it('does not call onAcknowledge until the warning step is confirmed', () => {
    const onAcknowledge = vi.fn();
    render(
      <FirstRunCredentialsModal email="owner@tenant-grill.local" password="s3cr3t-temp-pw" onAcknowledge={onAcknowledge} />
    );

    fireEvent.click(screen.getByText('Continuar'));
    expect(onAcknowledge).not.toHaveBeenCalled();
    expect(screen.getByText(/no se puede volver a mostrar/)).toBeTruthy();

    fireEvent.click(screen.getByText('Sí, ya la guardé'));
    expect(onAcknowledge).toHaveBeenCalledTimes(1);
  });

  it('lets the operator go back without acknowledging', () => {
    const onAcknowledge = vi.fn();
    render(
      <FirstRunCredentialsModal email="owner@tenant-grill.local" password="s3cr3t-temp-pw" onAcknowledge={onAcknowledge} />
    );

    fireEvent.click(screen.getByText('Continuar'));
    fireEvent.click(screen.getByText('Volver'));

    expect(screen.getByText('s3cr3t-temp-pw')).toBeTruthy();
    expect(onAcknowledge).not.toHaveBeenCalled();
  });
});
