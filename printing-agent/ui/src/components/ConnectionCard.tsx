import { useState } from 'react';
import { Wifi } from 'lucide-react';
import type { Status } from '../lib/types';
import Card from './Card';
import PairingSection from './PairingSection';

const DOT_COLOR: Record<Status['phase'], string> = {
  CONNECTED: 'bg-emerald-500',
  CONNECTING: 'bg-amber-500',
  RETRYING: 'bg-amber-500',
  UNPAIRED: 'bg-red-700'
};

function humanizeSince(iso: string | null): string {
  if (!iso) return 'nunca';
  const ms = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(ms / 60000);
  if (minutes < 1) return 'hace un momento';
  if (minutes < 60) return `hace ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 48) return `hace ${hours} h`;
  return `hace ${Math.floor(hours / 24)} d`;
}

export default function ConnectionCard({
  status,
  onPaired
}: {
  status: Status | null;
  onPaired: () => void;
}) {
  const needsPairing = status?.phase === 'UNPAIRED';
  const [showPairing, setShowPairing] = useState(false);
  const pairingVisible = needsPairing || showPairing;

  return (
    <Card icon={Wifi} title="Conexión">
      {!status ? (
        <p className="text-muted-foreground">Cargando…</p>
      ) : (
        <dl className="grid grid-cols-2 gap-y-1 text-sm">
          <dt className="text-muted-foreground">Estado</dt>
          <dd className="flex items-center gap-2">
            <span className={`inline-block h-2.5 w-2.5 rounded-full ${DOT_COLOR[status.phase]}`} />
            {status.detail ?? status.phase}
          </dd>
          <dt className="text-muted-foreground">Última vez visto</dt>
          <dd>{humanizeSince(status.lastSeen)}</dd>
          <dt className="text-muted-foreground">Impresoras</dt>
          <dd>{status.printerCount}</dd>
        </dl>
      )}

      {status && !pairingVisible && (
        <button
          className="text-sm text-primary underline mt-3 w-fit"
          onClick={() => setShowPairing(true)}
        >
          Volver a poner API key o código
        </button>
      )}

      {status && pairingVisible && (
        <PairingSection
          onPaired={() => {
            setShowPairing(false);
            onPaired();
          }}
          onCancel={needsPairing ? undefined : () => setShowPairing(false)}
        />
      )}
    </Card>
  );
}
