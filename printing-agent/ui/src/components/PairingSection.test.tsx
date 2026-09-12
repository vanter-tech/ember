import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import PairingSection from './PairingSection';
import * as api from '../lib/api';

describe('PairingSection', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('shows the server error message when pairing fails', async () => {
    vi.spyOn(api, 'pairWithCode').mockRejectedValue(new Error('Código inválido, usado o vencido.'));
    const onPaired = vi.fn();
    render(<PairingSection onPaired={onPaired} />);

    fireEvent.change(screen.getByPlaceholderText('Código de 10 caracteres'), { target: { value: 'ABCDEFGHIJ' } });
    fireEvent.click(screen.getByText('Emparejar'));

    await waitFor(() => expect(screen.getByText('Código inválido, usado o vencido.')).toBeTruthy());
    expect(onPaired).not.toHaveBeenCalled();
  });

  it('calls onPaired after a successful pairing', async () => {
    vi.spyOn(api, 'pairWithCode').mockResolvedValue({
      phase: 'CONNECTING', detail: null, lastSeen: null, agentId: null, printerCount: 0, recentJobs: []
    });
    const onPaired = vi.fn();
    render(<PairingSection onPaired={onPaired} />);

    fireEvent.change(screen.getByPlaceholderText('Código de 10 caracteres'), { target: { value: 'ABCDEFGHIJ' } });
    fireEvent.click(screen.getByText('Emparejar'));

    await waitFor(() => expect(onPaired).toHaveBeenCalledTimes(1));
  });
});
