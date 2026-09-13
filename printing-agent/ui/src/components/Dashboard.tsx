import { useEffect, useState } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { Router, RotateCw } from 'lucide-react';
import { getStatus } from '../lib/api';
import type { Status } from '../lib/types';
import { watchAgentShell, type AgentShellState } from '../lib/agent-events';
import { cardShellClass, IconBadge } from './Card';
import Button from './Button';
import ConnectionCard from './ConnectionCard';
import PrintersSection from './PrintersSection';
import JobsTable from './JobsTable';
import FooterActions from './FooterActions';

export default function Dashboard() {
  const [shellState, setShellState] = useState<AgentShellState>('starting');
  const [status, setStatus] = useState<Status | null>(null);

  useEffect(() => watchAgentShell(setShellState), []);

  async function refresh() {
    try {
      setStatus(await getStatus());
    } catch {
      // transient poll failure — retried on the next tick, not fatal (spec §4)
    }
  }

  useEffect(() => {
    if (shellState !== 'ready') return;
    refresh();
    const id = setInterval(refresh, 1500);
    return () => clearInterval(id);
  }, [shellState]);

  if (shellState === 'starting') {
    return <main className="p-4 text-muted-foreground">Iniciando Ember Agent…</main>;
  }
  if (shellState === 'timeout' || shellState === 'crashed') {
    return (
      <main className="p-4 flex flex-col gap-3">
        <p className="text-red-700">El agente no pudo iniciar.</p>
        <Button variant="primary" className="w-fit" onClick={() => invoke('restart_agent')}>
          Reintentar
        </Button>
      </main>
    );
  }

  return (
    <main className="h-full p-4 flex flex-col gap-4 max-w-4xl mx-auto min-h-0">
      <header className={`${cardShellClass} p-4 flex items-center justify-between gap-3 flex-wrap shrink-0`}>
        <div className="flex items-center gap-3 min-w-0">
          <IconBadge icon={Router} size="lg" />
          <div className="min-w-0">
            <h1 className="text-xl font-bold leading-tight">Ember Agent</h1>
            <p className="text-sm text-muted-foreground">
              Agente local de impresión y puente de hardware para estación POS
            </p>
          </div>
        </div>
        <Button variant="outline" className="shrink-0" onClick={() => invoke('restart_agent')}>
          <RotateCw className="h-4 w-4" />
          Reiniciar servicios
        </Button>
      </header>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 shrink-0">
        <ConnectionCard status={status} onPaired={refresh} />
        <PrintersSection />
      </div>

      <JobsTable jobs={status?.recentJobs ?? []} />

      <FooterActions />
    </main>
  );
}
