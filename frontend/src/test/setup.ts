import '@testing-library/jest-dom'

// jsdom ships no ResizeObserver; Radix UI primitives (Select, etc.) call it on mount.
if (!('ResizeObserver' in globalThis)) {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver
}
