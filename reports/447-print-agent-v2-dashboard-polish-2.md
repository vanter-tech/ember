# Report 447 — PRINT-AGENT-V2: button colors/shape, pairing accordion, tablet window size

**Predecessor Task:** report 446 — dashboard visual polish (badges, modal, rounded cards)

## Objective
Second round of itemized user feedback on the dashboard: make specific buttons red and more
rounded (matching the others), redesign the pairing modal to show two selectable options (each
with a description) that expand to a single input instead of the previous two-input form, and
fix the Tauri window to a tablet-sized resolution.

## Modified Files
- `printing-agent/ui/src/components/Button.tsx` (new — shared `primary`/`outline` button)
- `printing-agent/ui/src/components/PairingSection.tsx` (rewritten — accordion)
- `printing-agent/ui/src/components/PairingSection.test.tsx`
- `printing-agent/ui/src/components/PrintersSection.tsx`
- `printing-agent/ui/src/components/FooterActions.tsx`
- `printing-agent/ui/src/components/ConnectionCard.tsx`
- `printing-agent/ui/src/components/Dashboard.tsx`
- `printing-agent/src-tauri/tauri.conf.json`

## What Changed?
- New `Button.tsx`: `primary` (red `bg-primary`) / `outline` (bordered) variants, both
  `rounded-full` — now used by every button in the UI instead of one-off `rounded-md` classes,
  so the roundedness is actually consistent app-wide as requested.
- Turned **red** (`variant="primary"`): "Actualizar" (`PrintersSection`), "Volver a poner API
  key o código" (`ConnectionCard`), "Abrir carpeta de logs" and "Copiar diagnóstico"
  (`FooterActions`). Left **outline**: "Reiniciar servicios", "Cancelar" (not asked to be red;
  keeping Cancel non-primary is standard practice for a dismiss action).
- **`PairingSection` rewritten as an accordion**: two rows — "Código de emparejamiento" (`Hash`
  icon) and "API key" (`KeyRound` icon) — each with a one-line description. Clicking a row
  expands *only that row* (the other collapses), revealing exactly one data input (code or key,
  never both) plus a "Usar otra URL de backend" link that reveals the backend-URL field only if
  the user actually needs to change it (previously always shown, was the second of the "two
  inputs" the user wanted collapsed to one). Buttons swapped to the new `Button` component.
- **Test update**: `PairingSection.test.tsx`'s two existing tests now click "Código de
  emparejamiento" first to open that row before interacting with its input (matches the new
  required interaction); added a third test asserting only one option's input is present at a
  time and switching options swaps which one shows.
- **Window size**: `tauri.conf.json`'s main window `640x560` → `1024x768` (`minWidth`/`minHeight`
  `720x560` so it can't be shrunk to the point the 2-column grid breaks) — a standard
  tablet-class resolution, per the user's "como si fuese tableta" request.

## Why It Changed?
Direct, itemized user feedback after visually reviewing report 446: red+rounded buttons for
specific actions, replacing the old link-toggle pairing form with a clearer two-option
accordion (each option explained, only one data field visible at a time), and sizing the window
for a tablet-class POS display instead of the original compact utility-window dimensions.

## Verification
- `npm test` (printing-agent/ui): **5/5** (was 4 — +1 new accordion-exclusivity test; the 2
  existing tests updated for the new click-to-expand interaction).
- `npm run build`: clean.
- **Live visual verification**: same sidecar-shim technique as reports 445/446. Confirmed: all
  four buttons red pills, "Reiniciar servicios" still outline, opening the pairing button shows
  the two-option accordion with descriptions, clicking "Código de emparejamiento" expands to
  reveal exactly the code input + "Usar otra URL de backend" link + Cancelar/Emparejar (both
  through `Button`), matching the requested one-input-at-a-time behavior.
- Rebuilt the installer → `printing-agent/dist/EmberAgentSetup-0.1.1.exe` (now opens at
  1024×768) — user still needs to reinstall.
