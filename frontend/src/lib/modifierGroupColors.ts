// Muted, warm accents that sit on the app's neutral (white/zinc + brand red) surfaces: the color
// only identifies a group (dot + left edge), it never fills a surface. Literal class names so
// Tailwind picks them up; each group keeps one color (by id) everywhere.
export const GROUP_COLORS = [
  { dot: 'bg-red-800', accent: 'border-l-red-800' }, // ladrillo
  { dot: 'bg-orange-700', accent: 'border-l-orange-700' }, // terracota
  { dot: 'bg-amber-600', accent: 'border-l-amber-600' }, // ocre
  { dot: 'bg-lime-700', accent: 'border-l-lime-700' }, // oliva
  { dot: 'bg-emerald-600', accent: 'border-l-emerald-600' }, // salvia
  { dot: 'bg-slate-500', accent: 'border-l-slate-500' }, // pizarra
  { dot: 'bg-purple-800', accent: 'border-l-purple-800' }, // ciruela
  { dot: 'bg-stone-500', accent: 'border-l-stone-500' }, // piedra
]

export const colorForGroup = (id?: number) => GROUP_COLORS[Math.abs(id ?? 0) % GROUP_COLORS.length]
