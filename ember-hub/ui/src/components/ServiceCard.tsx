import { useEffect, useRef, useState, type ComponentType } from 'react';
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

/**
 * Reveals one script line every ~350ms while starting/stopping. Unlike the old version, lines are
 * NOT cleared once the phase moves on (e.g. STARTING -> RUNNING) — the last revealed script stays
 * around so the card can be reopened later and still show what happened, until the next start/stop
 * cycle replaces it.
 */
function useServiceLog(id: ServiceId, phase: ServicePhase): string[] {
  const [lines, setLines] = useState<string[]>([]);

  useEffect(() => {
    const script = phase === 'STARTING' ? START_SCRIPTS[id] : phase === 'STOPPING' ? STOP_SCRIPTS[id] : null;
    if (!script) return;

    setLines([]);
    let i = 0;
    const intervalId = setInterval(() => {
      i += 1;
      setLines(script.slice(0, i));
      if (i >= script.length) clearInterval(intervalId);
    }, 350);
    return () => clearInterval(intervalId);
  }, [id, phase]);

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

  const [expanded, setExpanded] = useState(starting || stopping || isError);
  const prevPhaseRef = useRef(phase);
  useEffect(() => {
    if (phase !== prevPhaseRef.current) {
      if (phase === 'STARTING' || phase === 'STOPPING' || phase === 'ERROR') {
        setExpanded(true);
      }
      prevPhaseRef.current = phase;
    }
  }, [phase]);

  const lines = useServiceLog(id, phase);
  const hasContent = isError || lines.length > 0;

  return (
    <section className={`${cardShellClass} p-4 flex flex-col min-w-0`}>
      <div className="flex items-center gap-3 min-w-0">
        <IconBadge icon={icon} />
        <h2 className="font-semibold text-lg flex-1 min-w-0">{title}</h2>
        <Badge variant={PHASE_VARIANT[phase]}>{PHASE_LABEL[phase]}</Badge>
        <button
          type="button"
          aria-label={expanded ? `Ocultar registro de ${title}` : `Mostrar registro de ${title}`}
          onClick={() => setExpanded((v) => !v)}
          className="shrink-0 rounded-full p-1 hover:bg-primary/10 cursor-pointer"
        >
          <ChevronDown className={`h-4 w-4 text-muted-foreground transition-transform ${expanded ? 'rotate-180' : ''}`} />
        </button>
      </div>
      {expanded && hasContent && (
        <div className="mt-3 flex flex-col gap-1 overflow-y-auto max-h-32">
          {isError ? (
            <p className="rounded-lg bg-[#8c1717] text-white font-mono text-xs px-2 py-1">{error}</p>
          ) : (
            lines.map((line, i) => (
              <p key={i} className="rounded-lg bg-[#8c1717] text-white font-mono text-xs px-2 py-1">
                {line}
              </p>
            ))
          )}
        </div>
      )}
    </section>
  );
}
