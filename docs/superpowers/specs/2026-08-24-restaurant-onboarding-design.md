# Restaurant Onboarding (Admin Wizard + Waiter Tour) — Diseño

> Estado: **aprobado**, listo para plan de implementación.

## 1. Motivación

Hoy, crear un restaurante desde el `/console` de operador de plataforma
(`PlatformRestaurantService.create`) solo crea el `Restaurant` y un usuario
`ADMIN` — nada más. Confirmado en el código:

- `RestaurantSettings` se auto-crea con defaults genéricos (moneda `$`, 0%
  impuesto) la primera vez que alguien **lee** la configuración
  (`SettingService.getSettings`), pero el sub-objeto `branding`
  (`businessName`, `legalName`, `ruc`, etc.) queda completamente vacío.
- `DiningTables` **nunca se crea automáticamente**. Solo aparecen mesas si
  un admin entra a Configuración → pestaña "Espacio" y guarda un número de
  mesas (`SettingService.updateSettings` → `syncDiningTables`).
- No existe ningún tour/onboarding en el frontend hoy — sin librería de
  tours instalada, sin componente propio.

Consecuencia observada: un restaurante recién creado (o recreado tras
borrar el anterior) queda funcionalmente vacío — la vista de mesero
(`Tables.tsx`) no muestra ninguna mesa, y Configuración → Branding aparece
en blanco — hasta que alguien lo configura manualmente a mano, sin que
nada lo indique o lo guíe.

**Objetivo:** que el primer ingreso de un admin a un restaurante recién
creado lo lleve, de forma obligatoria pero mínima, a dejarlo funcional
(al menos 1 mesa + nombre del negocio) — y que el primer ingreso del
mesero le muestre un recorrido breve de su propia pantalla, una vez que ya
hay mesas reales que mostrar.

## 2. Decisiones tomadas

### 2.1 Gating derivado, sin campos ni migraciones nuevas

No se agrega ninguna columna ni tabla nueva para marcar "onboarding
completado". El estado se **deriva** de datos que ya existen y que ya se
piden con un único `GET /settings`:

```
mostrar wizard  ⟺  payload.branding.businessName está vacío
               O  payload.space.totalTables es 0
```

Se prefirió sobre una bandera explícita (`Restaurant.onboardingCompletedAt`
o similar) porque:
- Cero migración, cero campo nuevo que mantener.
- Restaurantes que **ya** tienen mesas/branding configurados hoy (creados
  manualmente antes de este cambio) nunca verán el wizard — la condición
  ya se cumple, sin backfill.
- El caso borde de "un admin borra todas las mesas después y el wizard
  reaparece" se acepta deliberadamente: es razonable que un restaurante
  sin ninguna mesa activa vuelva a pedir el mínimo, no es un bug.

### 2.2 Wizard del admin — obligatorio hasta el mínimo

Un componente wrapper en el layout del admin hace `GET /settings` al
montar. Si la condición de 2.1 se cumple, renderiza
`AdminOnboardingWizard` en pantalla completa **en vez de** las rutas
normales del admin — no hay ruta nueva en el router, es una condición
sobre lo que ya se carga. Al completar el mínimo, se vuelve a pedir
`/settings`, la condición deja de cumplirse, y las rutas normales se
muestran sin recargar la página.

El wizard tiene 4 pantallas:

1. **Bienvenida** — sin datos, solo contexto ("Bienvenido a Ember —
   configuremos tu restaurante en 2 pasos rápidos").
2. **Paso 1 — Nombre del negocio** (`branding.businessName`, único campo
   requerido de este paso; reutiliza el mismo input que ya existe en
   `BrandingSettings.tsx`).
3. **Paso 2 — Número de mesas** (`space.totalTables`, mínimo 1; reutiliza
   el mismo input que ya existe en `SpaceSettings.tsx`).
4. **Confirmación** — indica que el restaurante ya está listo, con un
   enlace directo a Configuración para completar el resto (logo, colores,
   horarios, RUC, etc.) cuando quiera — nada de eso es obligatorio aquí.

Cada paso hace su propio `PUT /settings` (con el resto del payload actual
sin tocar, solo actualizando el campo de ese paso) al presionar
"Siguiente" — así, si el navegador se cierra a medio wizard, lo ya
guardado no se pierde y el próximo login retoma solo lo que falte (el
gating de 2.1 evalúa cada campo por separado, no "wizard completo sí/no").

El wizard se muestra a **cualquier** usuario ADMIN del restaurante que
inicie sesión mientras falte el mínimo — el estado es del restaurante, no
del usuario, así que dos admins distintos ven exactamente el mismo estado.

No hay endpoint de backend nuevo: `GET`/`PUT /settings` ya existen y ya
tienen el efecto colateral necesario (`syncDiningTables` crea las mesas
reales al guardar `space.totalTables`).

### 2.3 Tour del mesero — descubrimiento pasivo, no bloqueante

El mesero no configura nada del restaurante, así que no tiene un wizard
activo — tiene un tour pasivo de tooltips sobre su propia interfaz ya
existente, usando **react-joyride** (MIT, declarativo en React — se
prefirió sobre alternativas imperativas basadas en selectores DOM como
`driver.js`/`shepherd.js` por encajar mejor con el resto del código React
de este proyecto).

4 pasos, sobre pantallas que ya existen sin ningún cambio:

1. Vista general de mesas (`Tables.tsx`).
2. Cómo entrar/abrir una mesa (`TableInformation.tsx`).
3. Cómo agregar un ítem manualmente a una orden.
4. Cómo cobrar/cerrar una mesa (`ChargeTableModal.tsx`).

**Activación:** se muestra en el primer login del mesero para ese
restaurante, condicionado a que ya existan mesas reales (`GET /settings`
→ `space.totalTables > 0` — en la práctica, casi siempre cierto, dado que
el wizard del admin es obligatorio antes de que cualquiera pueda operar
con normalidad). Se guarda una bandera `ember-waiter-tour-seen-{userId}`
en `localStorage` al terminar o cerrar el tour, para no repetirlo en
logins futuros — mismo patrón client-side-only que ya usa hoy el store de
idioma (`localeStore`, Zustand `persist`).

## 3. Fuera de alcance

- **Roles kitchen/customer:** no piden ni "descubren" nada análogo al
  admin/mesero — no mencionados por el usuario, quedan fuera.
- **Campos de branding más allá de `businessName`** (logo, colores,
  horarios, RUC, wifi): siguen siendo 100% opcionales, completables en
  cualquier momento desde Configuración exactamente como hoy — el wizard
  deliberadamente no los pide.
- **Bandera explícita de "onboarding completado":** ver 2.1 — el caso
  borde de reaparición tras borrar todas las mesas se acepta, no se
  soluciona con estado adicional.
- **Cambios de backend:** ninguno. Este diseño es 100% frontend.

## 4. Manejo de errores

- Si falla el `PUT /settings` de un paso (red caída, 5xx, etc.): se
  muestra un error inline en el propio paso, no avanza, se puede
  reintentar — el progreso de pasos anteriores ya guardado no se pierde.
- Si `GET /settings` falla al cargar el layout del admin (ej. sin
  conexión): se muestra el estado de error/carga habitual de la app, no
  se asume ni se fuerza el wizard — evita mostrar un wizard incorrecto por
  un fallo de red transitorio.

## 5. Testing

- **Backend:** ninguno nuevo — `GET`/`PUT /settings` y `syncDiningTables`
  ya están cubiertos por tests existentes; este diseño no les cambia
  comportamiento.
- **Frontend:**
  - Test del componente de gating: dado un `GET /settings` mockeado con
    `businessName` vacío o `totalTables` en 0, renderiza el wizard; con
    ambos completos, renderiza las rutas normales.
  - Test de cada paso del wizard: envía el payload correcto a `PUT
    /settings`, avanza al siguiente paso solo si la respuesta es exitosa,
    muestra error inline si falla.
  - El tour de mesero (react-joyride) se verifica manualmente en
    navegador — no es una librería diseñada para snapshot/unit testing
    significativo de su comportamiento visual de highlight/tooltip.
