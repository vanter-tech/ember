import { KeyRound } from 'lucide-react';
import Card from './Card';
import Badge from './Badge';
import Button from './Button';
import type { LicenseSnapshot } from '../lib/types';

const STATUS_LABEL: Record<LicenseSnapshot['status'], string> = {
  OK: 'OK',
  SUSPENDED: 'Suspendida',
  MIGRATED: 'Migrada a Web (solo lectura)',
  NONE: 'Sin licencia'
};

const STATUS_VARIANT: Record<LicenseSnapshot['status'], 'success' | 'danger' | 'neutral'> = {
  OK: 'success',
  SUSPENDED: 'danger',
  MIGRATED: 'danger',
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
  onSelectLicense,
  onRemoveLicense
}: {
  license: LicenseSnapshot;
  onSelectLicense: () => void;
  onRemoveLicense: () => void;
}) {
  return (
    <Card
      icon={KeyRound}
      title="Licencia"
      badge={<Badge variant={STATUS_VARIANT[license.status]}>{STATUS_LABEL[license.status]}</Badge>}
    >
      {license.status === 'OK' && (
        <p className="text-sm text-muted-foreground mb-3">último contacto {humanizeSince(license.lastHeartbeatAt)}</p>
      )}
      {license.status === 'SUSPENDED' && (
        <p className="text-sm text-muted-foreground mb-3">suspendida {humanizeSince(license.suspendedSince)}</p>
      )}
      {license.status === 'MIGRATED' && (
        <p className="text-sm text-muted-foreground mb-3">
          Este restaurante ahora usa Ember Web. Tu Hub queda en modo consulta: puedes ver tu
          historial y descargar el Excel, pero no registrar ventas nuevas.
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button variant="primary" className="w-fit" onClick={onSelectLicense}>
          Seleccionar license.key…
        </Button>
        {license.status !== 'NONE' && (
          <Button variant="outline" className="w-fit" onClick={onRemoveLicense}>
            Eliminar license.key
          </Button>
        )}
      </div>
    </Card>
  );
}
