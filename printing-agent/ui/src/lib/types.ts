export type Phase = 'UNPAIRED' | 'CONNECTING' | 'CONNECTED' | 'RETRYING';

export interface JobRecord {
  at: string | null;
  role: string | null;
  queue: string | null;
  result: string | null;
  error: string | null;
}

export interface Status {
  phase: Phase;
  detail: string | null;
  lastSeen: string | null;
  agentId: string | null;
  printerCount: number;
  recentJobs: JobRecord[];
}

export interface DiscoveredPrinter {
  name: string;
  driverName: string;
  portName: string;
  inkjetGuess: boolean;
}
