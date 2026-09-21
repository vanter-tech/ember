export type ConnectStage = 'searching' | 'connecting' | 'connected';

const LABEL: Record<ConnectStage, string> = {
  searching: 'Buscando el servidor en la red…',
  connecting: 'Conectando…',
  connected: 'Conectado'
};

/**
 * The loading bar shown while a pairing code is being redeemed and the agent connects. Once the
 * agent really is connected the bar reads "Conectado" and the confirmation sits right below it.
 */
export default function ConnectProgress({ stage, progress }: { stage: ConnectStage; progress: number }) {
  const done = stage === 'connected';
  return (
    <div className="flex flex-col gap-2" role="status" aria-live="polite">
      <div
        className="relative h-8 rounded-full bg-muted overflow-hidden"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={done ? 100 : Math.round(progress)}
      >
        <div
          className={`h-full transition-all duration-300 ${done ? 'bg-emerald-300' : 'bg-amber-200'}`}
          style={{ width: `${done ? 100 : progress}%` }}
        />
        <span className="absolute inset-0 flex items-center justify-center text-xs font-medium text-zinc-900">
          {LABEL[stage]}
        </span>
      </div>
      {done && <p className="text-sm font-medium text-emerald-700">Conectado correctamente</p>}
    </div>
  );
}
