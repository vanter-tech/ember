import { KeyRound } from 'lucide-react';
import Card from './Card';
import Badge from './Badge';
import Button from './Button';
import type { LicenseSnapshot } from '../lib/types';

const STATUS_LABEL: Record<LicenseSnapshot['status'], string> = {
  OK: 'OK',
  SUSPENDED: 'Suspendida',
  NONE: 'Sin licencia'
};

const STATUS_VARIANT: Record<LicenseSnapshot['status'], 'success' | 'danger' | 'neutral'> = {
  OK: 'success',
  SUSPENDED: 'danger',
  NONE: 'neutral'
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

export default function LicenseCard({
  license,
  onSelectLicense
}: {
  license: LicenseSnapshot;
  onSelectLicense: () => void;
}) {
  return (
    <Card icon={KeyRound} title="Licencia">
      <div className="flex items-center gap-2 mb-3">
        <Badge variant={STATUS_VARIANT[license.status]}>{STATUS_LABEL[license.status]}</Badge>
        {license.status === 'OK' && (
          <span className="text-sm text-muted-foreground">último contacto {humanizeSince(license.lastHeartbeatAt)}</span>
        )}
        {license.status === 'SUSPENDED' && (
          <span className="text-sm text-muted-foreground">suspendida {humanizeSince(license.suspendedSince)}</span>
        )}
      </div>
      <Button variant="primary" className="w-fit" onClick={onSelectLicense}>
        Seleccionar license.key…
      </Button>
    </Card>
  );
}
