import { useState } from 'react';
import { pairWithApiKey, pairWithCode } from '../lib/api';

const DEFAULT_BACKEND = 'https://api.ember.vanter.net/v1';

export default function PairingSection({ onPaired }: { onPaired: () => void }) {
  const [mode, setMode] = useState<'code' | 'key'>('code');
  const [code, setCode] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [backendUrl, setBackendUrl] = useState(DEFAULT_BACKEND);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    setMessage('Procesando…');
    try {
      if (mode === 'key') {
        await pairWithApiKey(apiKey.trim(), backendUrl.trim());
      } else {
        await pairWithCode(code.trim().toUpperCase(), backendUrl.trim());
      }
      setMessage(null);
      onPaired();
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Error desconocido');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="rounded-lg border border-border p-4">
      <h2 className="font-semibold text-lg mb-3">Emparejar este agente</h2>
      {mode === 'code' ? (
        <input
          className="border border-border rounded-md px-2 py-1 w-full mb-2 uppercase"
          placeholder="Código de 10 caracteres"
          maxLength={10}
          value={code}
          onChange={(e) => setCode(e.target.value)}
        />
      ) : (
        <input
          className="border border-border rounded-md px-2 py-1 w-full mb-2"
          placeholder="API key"
          value={apiKey}
          onChange={(e) => setApiKey(e.target.value)}
        />
      )}
      <input
        className="border border-border rounded-md px-2 py-1 w-full mb-2 text-sm text-muted-foreground"
        value={backendUrl}
        onChange={(e) => setBackendUrl(e.target.value)}
      />
      <div className="flex items-center justify-between">
        <button
          className="text-sm text-primary underline"
          onClick={() => setMode(mode === 'code' ? 'key' : 'code')}
        >
          {mode === 'code' ? 'Tengo una API key' : 'Usar un código'}
        </button>
        <button
          className="bg-primary text-primary-foreground rounded-md px-4 py-1.5 disabled:opacity-50"
          disabled={busy}
          onClick={submit}
        >
          {mode === 'code' ? 'Emparejar' : 'Guardar'}
        </button>
      </div>
      {message && <p className="text-red-700 text-sm mt-2">{message}</p>}
    </section>
  );
}
