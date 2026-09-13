import { useEffect, useState, type ComponentType } from 'react';
import { ChevronDown } from 'lucide-react';
import { cardShellClass, IconBadge } from './Card';
import Badge from './Badge';
import { START_SCRIPTS, STOP_SCRIPTS, type ServiceId } from '../lib/logScripts';
import type { ServicePhase } from '../lib/types';

const PHASE_LABEL: Record<ServicePhase, string> = {
  STOPPED: 'Detenido',
  STARTING: 'Iniciando…',
  RUNNING: 'En ejecución',
  STOPPING: 'Deteniendo…',
  ERROR: 'Error'
};

const PHASE_VARIANT: Record<ServicePhase, 'success' | 'warning' | 'danger' | 'neutral'> = {
  STOPPED: 'neutral',
  STARTING: 'warning',
  RUNNING: 'success',
  STOPPING: 'warning',
  ERROR: 'danger'
};

/** Reveals one script line every ~350ms while `active`, resets when `active` goes false. */
function useTypedLog(script: readonly string[], active: boolean): string[] {
  const [lines, setLines] = useState<string[]>([]);

  useEffect(() => {
    if (!active) {
      setLines([]);
      return;
    }
    setLines([]);
    let i = 0;
    const id = setInterval(() => {
      i += 1;
      setLines(script.slice(0, i));
      if (i >= script.length) clearInterval(id);
    }, 350);
    return () => clearInterval(id);
  }, [active, script]);

  return lines;
}

export default function ServiceCard({
  id,
  icon,
  title,
  phase,
  error
}: {
  id: ServiceId;
  icon: ComponentType<{ className?: string }>;
  title: string;
  phase: ServicePhase;
  error: string | null;
}) {
  const starting = phase === 'STARTING';
  const stopping = phase === 'STOPPING';
  const isError = phase === 'ERROR';
  const expanded = starting || stopping || isError;

  const startLines = useTypedLog(START_SCRIPTS[id], starting);
  const stopLines = useTypedLog(STOP_SCRIPTS[id], stopping);

  return (
    <section className={`${cardShellClass} p-4 flex flex-col min-w-0`}>
      <div className="flex items-center gap-3 min-w-0">
        <IconBadge icon={icon} />
        <h2 className="font-semibold text-lg flex-1 min-w-0">{title}</h2>
        <Badge variant={PHASE_VARIANT[phase]}>{PHASE_LABEL[phase]}</Badge>
        {expanded && <ChevronDown className="h-4 w-4 text-muted-foreground shrink-0" />}
      </div>
      {expanded && (
        <div className="mt-3 rounded-2xl bg-[#8c1717] text-white font-mono text-xs p-3 overflow-auto max-h-32">
          {isError ? (
            <p className="mb-1 last:mb-0">{error}</p>
          ) : (
            (starting ? startLines : stopLines).map((line, i) => (
              <p key={i} className="mb-1 last:mb-0">{line}</p>
            ))
          )}
        </div>
      )}
    </section>
  );
}
