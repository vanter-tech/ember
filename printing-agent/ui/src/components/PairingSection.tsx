import { useState, type ComponentType } from 'react';
import { Hash, KeyRound, ChevronDown } from 'lucide-react';
import { getStatus, pairWithApiKey, pairWithCode } from '../lib/api';
import type { PairTarget } from '../lib/types';
import Button from './Button';
import ConnectProgress, { type ConnectStage } from './ConnectProgress';
import { IconBadge } from './Card';

type Mode = 'code' | 'key';

// After the pair request succeeds the agent still has to open its connection; we wait for the
// sidecar to report CONNECTED (polling its status) so the operator sees a real result.
const POLL_MS = 400;
const CONNECT_TIMEOUT_MS = 20000;
const SUCCESS_DWELL_MS = 1400;

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

async function waitUntilConnected(): Promise<void> {
  const deadline = Date.now() + CONNECT_TIMEOUT_MS;
  while (Date.now() < deadline) {
    const status = await getStatus();
    if (status.phase === 'CONNECTED') return;
    await sleep(POLL_MS);
  }
  throw new Error('No se pudo conectar. Verifica que el servidor esté encendido e inténtalo de nuevo.');
}

const OPTIONS: { mode: Mode; icon: ComponentType<{ className?: string }>; title: string; description: string }[] = [
  {
    mode: 'code',
    icon: Hash,
    title: 'Código de emparejamiento',
    description: 'Código de 10 caracteres generado desde Configuración → Impresión en el panel de administración. Válido por ~15 minutos.'
  },
  {
    mode: 'key',
    icon: KeyRound,
    title: 'API key',
    description: 'Usa una API key existente si ya tienes un agente configurado con una clave manual.'
  }
];

export default function PairingSection({
  onPaired,
  onCancel
}: {
  onPaired: () => void;
  onCancel?: () => void;
}) {
  const [openMode, setOpenMode] = useState<Mode | null>(null);
  const [code, setCode] = useState('');
  const [apiKey, setApiKey] = useState('');
  // Only "cloud" or "local" ever leaves the UI: the agent itself knows the cloud address and
  // finds the Hub on the network, so no server address is shown, typed or sent from here.
  const [target, setTarget] = useState<PairTarget>('cloud');
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [stage, setStage] = useState<ConnectStage | null>(null);
  const [progress, setProgress] = useState(0);

  async function submit(mode: Mode) {
    setBusy(true);
    setMessage(null);
    setProgress(8);
    setStage(target === 'local' ? 'searching' : 'connecting');
    // The bar creeps toward 90% while we wait; it only reaches 100% when really connected.
    const ticker = setInterval(() => setProgress((p) => Math.min(p + 3, 90)), 250);
    try {
      if (mode === 'key') {
        await pairWithApiKey(apiKey.trim(), target);
      } else {
        await pairWithCode(code.trim().toUpperCase(), target);
      }
      setStage('connecting');
      await waitUntilConnected();
      setProgress(100);
      setStage('connected');
      await sleep(SUCCESS_DWELL_MS);
      onPaired();
    } catch (e) {
      setStage(null);
      setMessage(e instanceof Error ? e.message : 'Error desconocido');
    } finally {
      clearInterval(ticker);
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <label className="flex flex-col gap-1">
        <span className="font-medium">Servidor</span>
        <select
          className="border border-border rounded-xl px-3 py-1.5 w-full bg-background"
          aria-label="Servidor"
          value={target}
          onChange={(e) => setTarget(e.target.value as PairTarget)}
        >
          <option value="cloud">Nube (Ember Cloud)</option>
          <option value="local">Local (Ember Hub en esta red)</option>
        </select>
        {target === 'local' && (
          <span className="text-sm text-muted-foreground">
            Busca automáticamente el Ember Hub en tu red. Debe estar encendido y en la misma red que esta PC.
          </span>
        )}
      </label>
      {OPTIONS.map((opt) => {
        const isOpen = openMode === opt.mode;
        return (
          <div key={opt.mode} className="rounded-2xl border border-border overflow-hidden">
            <button
              type="button"
              className="w-full flex items-start gap-3 p-4 text-left cursor-pointer"
              onClick={() => setOpenMode(isOpen ? null : opt.mode)}
            >
              <IconBadge icon={opt.icon} />
              <span className="flex-1 min-w-0">
                <span className="block font-medium">{opt.title}</span>
                <span className="block text-sm text-muted-foreground">{opt.description}</span>
              </span>
              <ChevronDown className={`h-4 w-4 text-muted-foreground shrink-0 mt-1 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
            </button>
            {isOpen && (
              <div className="p-4 pt-0 flex flex-col gap-2">
                {opt.mode === 'code' ? (
                  <input
                    className="border border-border rounded-xl px-3 py-1.5 w-full uppercase"
                    placeholder="Código de 10 caracteres"
                    maxLength={10}
                    value={code}
                    onChange={(e) => setCode(e.target.value)}
                  />
                ) : (
                  <input
                    className="border border-border rounded-xl px-3 py-1.5 w-full"
                    placeholder="API key"
                    value={apiKey}
                    onChange={(e) => setApiKey(e.target.value)}
                  />
                )}

                <div className="flex items-center justify-end gap-2 mt-1">
                  {onCancel && (
                    <Button variant="outline" onClick={onCancel}>
                      Cancelar
                    </Button>
                  )}
                  <Button variant="primary" disabled={busy} onClick={() => submit(opt.mode)}>
                    {opt.mode === 'code' ? 'Emparejar' : 'Guardar'}
                  </Button>
                </div>
              </div>
            )}
          </div>
        );
      })}
      {stage && <ConnectProgress stage={stage} progress={progress} />}
      {message && <p className="text-red-700 text-sm">{message}</p>}
    </div>
  );
}
