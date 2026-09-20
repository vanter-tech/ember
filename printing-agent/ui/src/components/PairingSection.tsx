import { useState, type ComponentType } from 'react';
import { Hash, KeyRound, ChevronDown } from 'lucide-react';
import { pairWithApiKey, pairWithCode } from '../lib/api';
import { DEFAULT_SERVER, normalizeServerUrl } from '../lib/server-url';
import Button from './Button';
import { IconBadge } from './Card';

type Mode = 'code' | 'key';

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
  const [server, setServer] = useState(DEFAULT_SERVER);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(mode: Mode) {
    const backendUrl = normalizeServerUrl(server);
    if (!backendUrl) {
      setMessage('Escribe la dirección del servidor.');
      return;
    }
    setBusy(true);
    setMessage('Procesando…');
    try {
      if (mode === 'key') {
        await pairWithApiKey(apiKey.trim(), backendUrl);
      } else {
        await pairWithCode(code.trim().toUpperCase(), backendUrl);
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
    <div className="flex flex-col gap-3">
      <label className="flex flex-col gap-1">
        <span className="font-medium">Servidor</span>
        <input
          className="border border-border rounded-xl px-3 py-1.5 w-full"
          aria-label="Servidor"
          value={server}
          onChange={(e) => setServer(e.target.value)}
        />
        <span className="text-sm text-muted-foreground">
          Ember en la nube por defecto. Para un local con Ember Hub escribe su dirección, por ejemplo{' '}
          <code>http://192.168.1.10:8080</code> (o <code>http://localhost:8080</code> si el Hub está en esta
          misma PC).
        </span>
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
      {message && <p className="text-red-700 text-sm">{message}</p>}
    </div>
  );
}
