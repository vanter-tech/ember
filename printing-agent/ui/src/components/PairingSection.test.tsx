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

    fireEvent.click(screen.getByText('Código de emparejamiento'));
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

    fireEvent.click(screen.getByText('Código de emparejamiento'));
    fireEvent.change(screen.getByPlaceholderText('Código de 10 caracteres'), { target: { value: 'ABCDEFGHIJ' } });
    fireEvent.click(screen.getByText('Emparejar'));

    await waitFor(() => expect(onPaired).toHaveBeenCalledTimes(1));
  });

  it('pairs against the cloud by default', async () => {
    const spy = vi.spyOn(api, 'pairWithCode').mockResolvedValue({
      phase: 'CONNECTING', detail: null, lastSeen: null, agentId: null, printerCount: 0, recentJobs: []
    });
    render(<PairingSection onPaired={vi.fn()} />);

    fireEvent.click(screen.getByText('Código de emparejamiento'));
    fireEvent.change(screen.getByPlaceholderText('Código de 10 caracteres'), { target: { value: 'abcdefghij' } });
    fireEvent.click(screen.getByText('Emparejar'));

    await waitFor(() => expect(spy).toHaveBeenCalledWith('ABCDEFGHIJ', 'https://api.ember.vanter.net/v1'));
  });

  it('pairs against the Hub address typed in the Servidor field, normalized', async () => {
    const spy = vi.spyOn(api, 'pairWithCode').mockResolvedValue({
      phase: 'CONNECTING', detail: null, lastSeen: null, agentId: null, printerCount: 0, recentJobs: []
    });
    render(<PairingSection onPaired={vi.fn()} />);

    fireEvent.change(screen.getByLabelText('Servidor'), { target: { value: '192.168.1.10:8080/' } });
    fireEvent.click(screen.getByText('Código de emparejamiento'));
    fireEvent.change(screen.getByPlaceholderText('Código de 10 caracteres'), { target: { value: 'ABCDEFGHIJ' } });
    fireEvent.click(screen.getByText('Emparejar'));

    await waitFor(() => expect(spy).toHaveBeenCalledWith('ABCDEFGHIJ', 'http://192.168.1.10:8080'));
  });

  it('refuses to pair with an empty server', async () => {
    const spy = vi.spyOn(api, 'pairWithCode');
    render(<PairingSection onPaired={vi.fn()} />);

    fireEvent.change(screen.getByLabelText('Servidor'), { target: { value: '  ' } });
    fireEvent.click(screen.getByText('Código de emparejamiento'));
    fireEvent.change(screen.getByPlaceholderText('Código de 10 caracteres'), { target: { value: 'ABCDEFGHIJ' } });
    fireEvent.click(screen.getByText('Emparejar'));

    await waitFor(() => expect(screen.getByText('Escribe la dirección del servidor.')).toBeTruthy());
    expect(spy).not.toHaveBeenCalled();
  });

  it('shows only one option expanded, with only one data input, at a time', () => {
    render(<PairingSection onPaired={vi.fn()} />);

    expect(screen.queryByPlaceholderText('Código de 10 caracteres')).toBeNull();
    expect(screen.queryByPlaceholderText('API key')).toBeNull();

    fireEvent.click(screen.getByText('Código de emparejamiento'));
    expect(screen.getByPlaceholderText('Código de 10 caracteres')).toBeTruthy();
    expect(screen.queryByPlaceholderText('API key')).toBeNull();

    fireEvent.click(screen.getByText('API key'));
    expect(screen.getByPlaceholderText('API key')).toBeTruthy();
    expect(screen.queryByPlaceholderText('Código de 10 caracteres')).toBeNull();
  });
});
