// Muted, warm accents that sit on the app's neutral (white/zinc + brand red) surfaces: the color
// only identifies a group (dot + left edge), it never fills a surface. Literal class names so
// Tailwind picks them up; each group keeps one color (by id) everywhere.
export const GROUP_COLORS = [
  { dot: 'bg-red-800', accent: 'border-l-red-800', badge: 'border-red-300 bg-red-50 text-red-900', badgeOn: 'border-red-700 bg-red-200 text-red-950' }, // ladrillo
  { dot: 'bg-orange-700', accent: 'border-l-orange-700', badge: 'border-orange-300 bg-orange-50 text-orange-900', badgeOn: 'border-orange-700 bg-orange-200 text-orange-950' }, // terracota
  { dot: 'bg-amber-600', accent: 'border-l-amber-600', badge: 'border-amber-300 bg-amber-50 text-amber-900', badgeOn: 'border-amber-700 bg-amber-200 text-amber-950' }, // ocre
  { dot: 'bg-lime-700', accent: 'border-l-lime-700', badge: 'border-lime-300 bg-lime-50 text-lime-900', badgeOn: 'border-lime-700 bg-lime-200 text-lime-950' }, // oliva
  { dot: 'bg-emerald-600', accent: 'border-l-emerald-600', badge: 'border-emerald-300 bg-emerald-50 text-emerald-900', badgeOn: 'border-emerald-700 bg-emerald-200 text-emerald-950' }, // salvia
  { dot: 'bg-slate-500', accent: 'border-l-slate-500', badge: 'border-slate-300 bg-slate-50 text-slate-900', badgeOn: 'border-slate-700 bg-slate-200 text-slate-950' }, // pizarra
  { dot: 'bg-purple-800', accent: 'border-l-purple-800', badge: 'border-purple-300 bg-purple-50 text-purple-900', badgeOn: 'border-purple-700 bg-purple-200 text-purple-950' }, // ciruela
  { dot: 'bg-stone-500', accent: 'border-l-stone-500', badge: 'border-stone-300 bg-stone-50 text-stone-900', badgeOn: 'border-stone-700 bg-stone-200 text-stone-950' }, // piedra
]

export const colorForGroup = (id?: number) => GROUP_COLORS[Math.abs(id ?? 0) % GROUP_COLORS.length]
