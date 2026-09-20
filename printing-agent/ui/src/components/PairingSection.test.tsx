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

  const connecting = {
    phase: 'CONNECTING' as const, detail: null, lastSeen: null, agentId: null, printerCount: 0, recentJobs: []
  };

  function pairWithCodeUsing(container: HTMLElement) {
    fireEvent.click(screen.getByText('Código de emparejamiento'));
    fireEvent.change(screen.getByPlaceholderText('Código de 10 caracteres'), { target: { value: 'abcdefghij' } });
    fireEvent.click(screen.getByText('Emparejar'));
    return container;
  }

  it('pairs against the cloud by default', async () => {
    const spy = vi.spyOn(api, 'pairWithCode').mockResolvedValue(connecting);
    const { container } = render(<PairingSection onPaired={vi.fn()} />);

    pairWithCodeUsing(container);

    await waitFor(() => expect(spy).toHaveBeenCalledWith('ABCDEFGHIJ', 'cloud'));
  });

  it('pairs against the Hub found on the network when Local is chosen', async () => {
    const spy = vi.spyOn(api, 'pairWithCode').mockResolvedValue(connecting);
    const { container } = render(<PairingSection onPaired={vi.fn()} />);

    fireEvent.change(screen.getByLabelText('Servidor'), { target: { value: 'local' } });
    pairWithCodeUsing(container);

    await waitFor(() => expect(spy).toHaveBeenCalledWith('ABCDEFGHIJ', 'local'));
  });

  it('offers only Nube and Local, and never shows or types a server address', () => {
    const { container } = render(<PairingSection onPaired={vi.fn()} />);

    const select = screen.getByLabelText('Servidor') as HTMLSelectElement;
    expect(Array.from(select.options).map((o) => o.text)).toEqual([
      'Nube (Ember Cloud)',
      'Local (Ember Hub en esta red)'
    ]);
    expect(container.querySelector('input')).toBeNull();

    fireEvent.change(select, { target: { value: 'local' } });
    fireEvent.click(screen.getByText('Código de emparejamiento'));

    const text = container.textContent ?? '';
    expect(text).not.toContain('http');
    expect(text).not.toContain('ember.vanter');
    expect(text).not.toContain('api.');
  });

  it('tells the operator it is searching the network while pairing locally', async () => {
    let finish: (v: typeof connecting) => void = () => {};
    vi.spyOn(api, 'pairWithCode').mockReturnValue(new Promise((resolve) => { finish = resolve; }));
    const { container } = render(<PairingSection onPaired={vi.fn()} />);

    fireEvent.change(screen.getByLabelText('Servidor'), { target: { value: 'local' } });
    pairWithCodeUsing(container);

    await waitFor(() => expect(screen.getByText('Buscando el servidor en la red…')).toBeTruthy());
    finish(connecting);
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
