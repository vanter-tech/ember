import { useEffect, useState } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { getStatus } from '../lib/api';
import type { Status } from '../lib/types';
import { watchAgentShell, type AgentShellState } from '../lib/agent-events';
import StatusSection from './StatusSection';
import PairingSection from './PairingSection';
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
        <button
          className="bg-primary text-primary-foreground rounded-md px-4 py-1.5 w-fit"
          onClick={() => invoke('restart_agent')}
        >
          Reintentar
        </button>
      </main>
    );
  }

  const needsPairing = status?.phase === 'UNPAIRED';

  return (
    <main className="p-4 flex flex-col gap-4 max-w-2xl mx-auto">
      <header className="flex items-center gap-2">
        <h1 className="text-xl font-bold">Ember Agent</h1>
      </header>
      <StatusSection status={status} />
      {needsPairing && <PairingSection onPaired={refresh} />}
      <PrintersSection />
      <JobsTable jobs={status?.recentJobs ?? []} />
      <FooterActions />
    </main>
  );
}
