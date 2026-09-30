/** Short attention tone for a new pending cash receipt. Best-effort: browsers may block audio before a user gesture. */
export const beep = () => {
  try {
    const Ctx =
      window.AudioContext ??
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!Ctx) return
    const ctx = new Ctx()
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.frequency.value = 880
    gain.gain.value = 0.1
    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.onended = () => void ctx.close()
    osc.start()
    osc.stop(ctx.currentTime + 0.25)
  } catch {
    // the toast still shows
  }
}
