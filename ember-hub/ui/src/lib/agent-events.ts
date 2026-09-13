import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';

export type AgentShellState = 'starting' | 'ready' | 'crashed' | 'timeout';

export function watchAgentShell(
  onChange: (state: AgentShellState) => void,
  timeoutMs = 5000
): () => void {
  let settled = false;
  let pollId: ReturnType<typeof setInterval> | null = null;

  const timeout = setTimeout(() => {
    if (!settled) {
      settled = true;
      if (pollId) clearInterval(pollId);
      onChange('timeout');
    }
  }, timeoutMs);

  // Polls get_port instead of relying solely on the "agent-ready" event: the sidecar's stdout
  // reader can emit that event before this listener is registered (webview still loading React),
  // silently dropping it since Tauri events aren't buffered for late subscribers.
  async function poll() {
    if (settled) return;
    const port = await invoke<number>('get_port').catch(() => 0);
    if (port > 0 && !settled) {
      settled = true;
      clearTimeout(timeout);
      if (pollId) clearInterval(pollId);
      onChange('ready');
    }
  }
  poll();
  pollId = setInterval(poll, 200);

  const unlistenCrashed = listen('agent-crashed', () => {
    onChange('crashed');
  });

  return () => {
    clearTimeout(timeout);
    if (pollId) clearInterval(pollId);
    unlistenCrashed.then((f) => f());
  };
}
