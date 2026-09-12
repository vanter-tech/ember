import { useEffect, useState } from 'react';
import { getStatus } from '../lib/api';
import type { Status } from '../lib/types';
import StatusSection from './StatusSection';
import PairingSection from './PairingSection';
import PrintersSection from './PrintersSection';
import JobsTable from './JobsTable';
import FooterActions from './FooterActions';

export default function Dashboard() {
  const [status, setStatus] = useState<Status | null>(null);

  async function refresh() {
    try {
      setStatus(await getStatus());
    } catch {
      // transient poll failure — retried on the next tick, not fatal (spec §4)
    }
  }

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, 1500);
    return () => clearInterval(id);
  }, []);

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
