import type { components } from '@/lib/backend-types'

type SettingsPayload = components['schemas']['SettingsPayload']
type DaySchedule = components['schemas']['DaySchedule']
type Day = NonNullable<DaySchedule['day']>

const DAYS: Day[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
// ASCII on purpose: it mirrors what the thermal printer prints (see the backend's ReceiptBusinessInfo).
const ABBR: Record<Day, string> = {
  MONDAY: 'Lun',
  TUESDAY: 'Mar',
  WEDNESDAY: 'Mie',
  THURSDAY: 'Jue',
  FRIDAY: 'Vie',
  SATURDAY: 'Sab',
  SUNDAY: 'Dom',
}

const signature = (s: DaySchedule): string | null => {
  if (s.closed) return 'cerrado'
  if (!s.openTime?.trim() || !s.closeTime?.trim()) return null
  return `${s.openTime.trim()}-${s.closeTime.trim()}`
}

const scheduleGroups = (schedule: DaySchedule[]): string[] => {
  const groups: string[] = []
  let runSignature: string | null = null
  let runStart: Day | null = null
  let runEnd: Day | null = null
  const flush = () => {
    if (runSignature && runStart && runEnd) {
      const label = runStart === runEnd ? ABBR[runStart] : `${ABBR[runStart]}-${ABBR[runEnd]}`
      groups.push(`${label} ${runSignature}`)
    }
  }
  for (const day of DAYS) {
    const entry = schedule.find((s) => s.day === day)
    const sig = entry ? signature(entry) : null
    if (sig !== null && sig === runSignature) {
      runEnd = day
      continue
    }
    flush()
    runSignature = sig
    runStart = sig === null ? null : day
    runEnd = runStart
  }
  flush()
  return groups
}

/** Opening hours as the receipt prints them: runs of identical consecutive days, else the Branding range. */
export const hoursLines = (settings?: SettingsPayload): string[] => {
  const groups = scheduleGroups(settings?.businessHours?.schedule ?? [])
  if (groups.length === 0) {
    const open = settings?.branding?.openingTime?.trim()
    const close = settings?.branding?.closingTime?.trim()
    return open && close ? [`Horario: ${open} - ${close}`] : []
  }
  return groups.length === 1 ? [`Horario: ${groups[0]}`] : ['Horario:', ...groups]
}

/** The block under the receipt header: RUC/address/phone (opt-in) then the opening hours. */
export const businessInfoLines = (
  settings: SettingsPayload | undefined,
  opts: { showHours: boolean; showInfo: boolean }
): string[] => {
  const lines: string[] = []
  if (opts.showInfo) {
    const branding = settings?.branding
    if (branding?.ruc?.trim()) lines.push(`RUC: ${branding.ruc.trim()}`)
    if (branding?.address?.trim()) lines.push(branding.address.trim())
    if (branding?.phone?.trim()) lines.push(`Tel: ${branding.phone.trim()}`)
  }
  if (opts.showHours) lines.push(...hoursLines(settings))
  return lines
}
