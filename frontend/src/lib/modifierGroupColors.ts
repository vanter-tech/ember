// Literal class names so Tailwind picks them up; each group keeps one color (by id) everywhere.
export const GROUP_COLORS = [
  { idle: 'border-rose-300 bg-rose-100 text-rose-800 hover:bg-rose-200', selected: 'border-rose-600 bg-rose-600 text-white', pill: 'border-rose-300 bg-rose-100 text-rose-800 hover:bg-rose-200' },
  { idle: 'border-orange-300 bg-orange-100 text-orange-800 hover:bg-orange-200', selected: 'border-orange-600 bg-orange-600 text-white', pill: 'border-orange-300 bg-orange-100 text-orange-800 hover:bg-orange-200' },
  { idle: 'border-amber-300 bg-amber-100 text-amber-800 hover:bg-amber-200', selected: 'border-amber-600 bg-amber-600 text-white', pill: 'border-amber-300 bg-amber-100 text-amber-800 hover:bg-amber-200' },
  { idle: 'border-lime-300 bg-lime-100 text-lime-800 hover:bg-lime-200', selected: 'border-lime-600 bg-lime-600 text-white', pill: 'border-lime-300 bg-lime-100 text-lime-800 hover:bg-lime-200' },
  { idle: 'border-emerald-300 bg-emerald-100 text-emerald-800 hover:bg-emerald-200', selected: 'border-emerald-600 bg-emerald-600 text-white', pill: 'border-emerald-300 bg-emerald-100 text-emerald-800 hover:bg-emerald-200' },
  { idle: 'border-sky-300 bg-sky-100 text-sky-800 hover:bg-sky-200', selected: 'border-sky-600 bg-sky-600 text-white', pill: 'border-sky-300 bg-sky-100 text-sky-800 hover:bg-sky-200' },
  { idle: 'border-indigo-300 bg-indigo-100 text-indigo-800 hover:bg-indigo-200', selected: 'border-indigo-600 bg-indigo-600 text-white', pill: 'border-indigo-300 bg-indigo-100 text-indigo-800 hover:bg-indigo-200' },
  { idle: 'border-fuchsia-300 bg-fuchsia-100 text-fuchsia-800 hover:bg-fuchsia-200', selected: 'border-fuchsia-600 bg-fuchsia-600 text-white', pill: 'border-fuchsia-300 bg-fuchsia-100 text-fuchsia-800 hover:bg-fuchsia-200' },
]

export const colorForGroup = (id?: number) => GROUP_COLORS[Math.abs(id ?? 0) % GROUP_COLORS.length]
