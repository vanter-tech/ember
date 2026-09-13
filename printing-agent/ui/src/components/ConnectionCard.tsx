import { useState } from 'react';
import { Wifi, KeyRound } from 'lucide-react';
import type { Status } from '../lib/types';
import Card from './Card';
import Badge from './Badge';
import Button from './Button';
import Modal from './Modal';
import PairingSection from './PairingSection';

const PHASE_VARIANT: Record<Status['phase'], 'success' | 'warning' | 'danger'> = {
  CONNECTED: 'success',
  CONNECTING: 'warning',
  RETRYING: 'warning',
  UNPAIRED: 'danger'
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
        <dl className="grid grid-cols-2 gap-y-2 text-sm items-center">
          <dt className="text-muted-foreground">Estado</dt>
          <dd>
            <Badge variant={PHASE_VARIANT[status.phase]}>{status.detail ?? status.phase}</Badge>
          </dd>
          <dt className="text-muted-foreground">Última vez visto</dt>
          <dd>{humanizeSince(status.lastSeen)}</dd>
          <dt className="text-muted-foreground">Impresoras</dt>
          <dd>{status.printerCount}</dd>
        </dl>
      )}

      {status && !needsPairing && (
        <Button variant="primary" className="mt-3 w-fit" onClick={() => setShowPairing(true)}>
          <KeyRound className="h-4 w-4" />
          Volver a poner API key o código
        </Button>
      )}

      {pairingVisible && (
        <Modal
          title="Emparejar este agente"
          onClose={needsPairing ? undefined : () => setShowPairing(false)}
        >
          <PairingSection
            onPaired={() => {
              setShowPairing(false);
              onPaired();
            }}
            onCancel={needsPairing ? undefined : () => setShowPairing(false)}
          />
        </Modal>
      )}
    </Card>
  );
}
