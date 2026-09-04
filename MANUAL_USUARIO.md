# Manual de Usuario — Ember

**Guía operativa para el personal del restaurante**

Este manual describe cómo usar Ember en el día a día, tal como se comporta el sistema hoy —
incluyendo un puñado de advertencias sobre comportamientos que todavía no funcionan como deberían
(marcadas con ⚠️ y su referencia al informe técnico `QA_SIMULATION_REPORT.md`). Está pensado para
el personal del restaurante, no para desarrolladores: no asume conocimiento técnico previo.

---

## Índice

1. [Antes de empezar](#1-antes-de-empezar)
2. [Administrador (ADMIN)](#2-administrador-admin)
3. [Mesero (WAITER)](#3-mesero-waiter)
4. [Cocina (KITCHEN)](#4-cocina-kitchen)
5. [Comensal (CUSTOMER)](#5-comensal-customer)
6. [Preguntas frecuentes y solución de problemas](#6-preguntas-frecuentes-y-solución-de-problemas)

---

## 1. Antes de empezar

### 1.1 Roles del sistema

Ember tiene cuatro tipos de usuario, cada uno con su propia vista al iniciar sesión:

| Rol | Quién lo usa | Qué puede hacer |
|---|---|---|
| **Administrador** | Dueño/gerente | Configuración del restaurante, menú, personal, analíticas, caja general |
| **Mesero** | Personal de sala | Abrir mesas, tomar pedidos, cobrar, cerrar caja de su turno |
| **Cocina** | Personal de cocina | Ver y actualizar el estado de los pedidos en la pantalla de cocina (KDS) |
| **Comensal** | Cliente | Escanear/ingresar un código de mesa, ver el menú, pedir, pagar su parte |

### 1.2 Cómo iniciar sesión

1. Abre la dirección web del restaurante en el navegador.
2. Si ya usaste este dispositivo antes, verás tarjetas de **"Acceso rápido"** con el nombre de
   empleados que iniciaron sesión anteriormente en ese equipo. Toca tu tarjeta e ingresa tu **PIN**
   de 4 a 6 dígitos (lo asigna el Administrador — ver §2.4).
   > ⚠️ **En un dispositivo compartido de piso**, cualquier persona que se acerque a la pantalla de
   > login puede ver el nombre y el correo de los empleados que ya iniciaron sesión ahí, sin
   > necesidad de contraseña. Si esto te preocupa, pide al equipo técnico que revise `E-21` del
   > informe de QA antes de dejar el dispositivo desatendido en un lugar visible al público.
3. Si es la primera vez, o el PIN aún no está configurado, toca **"Use another account"** e
   ingresa tu correo y contraseña.
4. Tras iniciar sesión correctamente verás un mensaje verde "Login successful!".

> ⚠️ **Pantalla en blanco tras iniciar sesión (ADMIN y MESERO).** Es normal que, justo después de
> ver "Login successful!", la pantalla quede completamente en blanco por un instante, con sólo la
> barra superior y el menú flotante inferior visibles. **No es un error ni significa que el sistema
> se colgó** — simplemente toca cualquier ícono del menú inferior (por ejemplo "Analytics" o
> "Tables") para entrar a tu vista de trabajo. El equipo técnico ya tiene identificado este
> comportamiento (`E-11`) y debería redirigirte automáticamente en una futura actualización.

### 1.3 El menú flotante inferior

En casi todas las pantallas verás una barra flotante en la parte inferior con íconos redondos —
es tu navegación principal. Los íconos disponibles dependen de tu rol.

---

## 2. Administrador (ADMIN)

### 2.1 Panel de Analíticas

Toca el ícono de gráfico de barras en el menú inferior. Verás:

- **Total Revenue** (ingresos totales), **Active Sessions** (mesas activas ahora), **Average
  Ticket** (ticket promedio).
- Un gráfico de ventas en el tiempo, filtrable por Día / Semana / Mes / Año.
- Productos más vendidos y ventas por categoría.
- Analítica de mesas: cuáles rotan más y con qué duración promedio.

> ⚠️ Si ves un ingreso total que no cuadra con lo que realmente entró a caja, revisa primero si
> hubo algún cobro registrado dos veces por error (ver `E-04` en el informe técnico) antes de
> asumir que la analítica está mal calculada — el panel simplemente suma lo que quedó registrado.

### 2.2 Catálogo (Categorías, Ítems, Modificadores, Inventario)

Toca el ícono de "tienda"/catálogo en el menú inferior.

1. **Categorías** — agrupan los platillos del menú (ej. "Pollo", "Bebidas"). Botón **"+ New
   category"** arriba a la derecha. Cada tarjeta de categoría muestra cuántos productos contiene;
   toca la tarjeta para entrar y gestionar sus ítems.
2. **Modifier groups** — opciones que se agregan a un platillo (ej. "Término de la carne",
   "Extra queso"), con mínimo/máximo de selecciones configurables por grupo.
3. **Inventory** — insumos y su stock; permite registrar reabastecimientos.

### 2.3 Configuración del restaurante (Settings)

Toca el ícono de engranaje. Está organizado en pestañas:

| Pestaña | Qué configura |
|---|---|
| **Branding & Business** | Nombre, horario, WiFi, color de marca |
| **Menu** | Mostrar/ocultar ítems agotados, búsqueda de ítems |
| **Billing** | Símbolo de moneda, **tasa de impuesto (%)**, propinas sugeridas, reglas de impuesto |
| **Hardware** | Impresión automática de comandas y recibos |
| **Space** | Número total de mesas |
| **Schedule** | Horario de atención (usado para calcular cuándo vence el turno de caja) |
| **Loyalty** | Programa de puntos/fidelización |

> ⚠️ **La tasa de impuesto configurada aquí todavía no se aplica a las cuentas reales** (`E-05`
> del informe técnico). Si tu restaurante cobra IVA u otro impuesto, **no confíes en este campo
> por ahora** — verifica manualmente que el monto cobrado en cada cuenta incluya el impuesto
> correcto hasta que el equipo técnico confirme que quedó corregido. Además, el panel del mesero
> siempre muestra "10%" de impuesto en la vista previa de la cuenta sin importar lo que
> configures aquí — ese 10% mostrado tampoco es real, es sólo un número de ejemplo que quedó fijo
> en la pantalla.

> ⚠️ Cambiar el **número de mesas** en "Space" con mesas ya ocupadas puede desactivar mesas en uso.
> Hazlo únicamente al abrir o cerrar el restaurante, nunca a media jornada.

### 2.4 Personal (Staff)

Toca el ícono de personas.

1. Verás tarjetas agrupadas por rol (Cocina, Comedor, Administración) con el nombre, turno, tipo
   de contrato y ubicación de cada persona.
2. **"+ New employee"** para dar de alta a alguien nuevo — se te pedirá nombre, correo, contraseña
   temporal, rol, puesto, turno, tipo de contrato y ubicación (todos los campos son obligatorios).
3. Toca **"Profile"** en la tarjeta de un empleado para editar sus datos o **configurar su PIN de
   acceso rápido**:
   - Escribe el PIN (4 a 6 dígitos) y repítelo en "Confirm PIN".
   - Toca **"Add PIN"**. Verás un mensaje verde "Quick-login PIN saved."
   - > ⚠️ Después de guardar, el modal puede seguir mostrando "No PIN" aunque el PIN ya haya
     > quedado guardado correctamente (`E-13`). **No lo configures de nuevo pensando que falló** —
     > cierra el modal, espera un par de segundos y vuelve a abrirlo; si sigue sin actualizarse,
     > pide al empleado que intente iniciar sesión con el PIN nuevo directamente, funciona aunque
     > la pantalla no lo confirme visualmente.
4. **El ícono "⋯" en la tarjeta de un empleado NO abre un menú** — es un botón directo de
   **desactivar** a esa persona (te pedirá confirmación antes de aplicarlo, así que un toque
   accidental no desactiva a nadie sin que confirmes). Si buscas editar en vez de desactivar, usa
   siempre el botón **"Profile"**.
5. Un empleado desactivado conserva su historial (pagos, turnos de caja) pero pierde acceso al
   sistema.

### 2.5 Caja general y reportes

Desde el ícono de tarjeta de caja puedes ver el historial de turnos de todos los meseros y el
**reporte diario** consolidado (ventas en efectivo, ventas digitales, diferencias de arqueo).

---

## 3. Mesero (WAITER)

### 3.1 Antes de tomar la primera mesa: abre tu caja

1. Al iniciar sesión, si tienes un turno de caja de un día anterior sin cerrar, verás un aviso
   **"The [fecha] register was never closed"**. Te conviene cerrarlo antes de seguir — toca
   **"Close the [fecha] register"**, cuenta el efectivo físico de la caja e ingrésalo en "Counted
   cash", luego **"Confirm count"**.
   > ⚠️ Si el sistema no te deja cerrar el turno porque **todavía hay mesas abiertas**, no verás
   > ningún mensaje de error en el diálogo — simplemente parecerá que no pasó nada al tocar
   > "Confirm count" (`E-13`/hallazgo de caja en el informe técnico). Cierra o cobra primero las
   > mesas pendientes y vuelve a intentarlo.
2. Si no tienes turno abierto, ve al ícono de caja y toca **"Open shift"**, ingresa el fondo de
   caja inicial ("opening float") y confirma.

### 3.2 Abrir una mesa

1. Toca el ícono de "Tables" (mesas) en el menú inferior. Verás el plano del salón: mesas en
   **rojo = ocupadas**, en **gris = libres**.
2. Toca una mesa libre. En el panel de la derecha verás **"Open table"**. Al tocarlo se crea la
   sesión y se genera un **código de 5 caracteres** que debes compartir con los comensales (o un
   código QR, si tu restaurante lo tiene impreso en la mesa) para que se unan desde su celular.

### 3.3 Ver y gestionar una mesa ocupada

Toca una mesa roja → **"View Information"** para entrar al detalle completo:

- **Order details** — lista de ítems pedidos, con botón para eliminar cada uno (🗑️).
- **Participants** — comensales que se unieron a la mesa.
- **Summary** — subtotal, impuestos y total (ver advertencia de impuestos en §2.3).
- **Activity** — bitácora de eventos de la mesa (pedidos, transferencias, etc).
- Botones de acción arriba: **Print Bill**, **Transfer** (pasar la mesa a otro mesero), **Add
  Item** (agregar un platillo directamente, por ejemplo si el comensal pide de viva voz sin usar
  su celular).

> ⚠️ **Si agregas un ítem con "Add Item" sin asignarlo a un comensal específico** (queda etiquetado
> como **"Mesa"**), ese ítem puede impedir que los comensales de esa mesa confirmen su propio
> pedido desde su celular — verán un error al tocar "Confirmar" (`E-06` del informe técnico). Si
> esto pasa, hasta que el equipo técnico corrija el problema, **toma tú mismo el pedido completo
> de la mesa manualmente con "Add Item"** en vez de dejar que los comensales confirmen desde su
> celular, para no bloquear el flujo. Por el mismo motivo, **eliminar un ítem "Mesa" también
> puede fallar** (`E-07`) — si te da un error al intentar quitarlo, repórtalo al equipo técnico en
> vez de reintentar varias veces.

### 3.4 Cobrar una mesa

1. Desde el detalle de la mesa, toca **"Charge table"** (o desde el panel resumido, el botón rojo
   con el mismo nombre).
2. Elige el método de división: **por consumo** (cada quien paga lo suyo) o **en partes iguales**.
3. Para cada participante, registra su pago:
   - **Pago físico (efectivo)**: ingresa el monto exacto que coincide con su parte — el sistema
     rechaza montos que no coincidan.
     > ⚠️ **No toques "Registrar pago" dos veces para el mismo comensal**, incluso si la pantalla
     > parece tardar en responder. Hoy el sistema **no bloquea un cobro duplicado** (`E-04`): un
     > doble clic puede registrar el mismo pago dos veces, haciendo que tu caja parezca tener más
     > dinero del que realmente recibiste. Si sospechas que pasó, revisa
     > "Historial de pagos" de esa cuenta antes de cerrar tu turno.
   - **Pago digital**: se genera un cobro pendiente que el comensal confirma desde su celular, o
     tú lo confirmas manualmente una vez verificado.
4. Cuando todos los splits quedan en **PAID**, la mesa se cierra automáticamente y vuelve a
   aparecer libre en el plano de salón.
5. Si un comensal se retira sin pagar su parte y aún no había consumido nada facturable, puedes
   **redistribuir** su parte entre los que quedan desde el mismo panel.

### 3.5 Cerrar tu turno de caja

Al final de tu turno, ve al ícono de caja → **"Close shift"**, cuenta el efectivo físico e
ingrésalo. El sistema te mostrará la diferencia entre lo esperado y lo contado. Si aparece un
"Cannot close: N table(s) still have an open session", significa que aún tienes mesas abiertas —
ciérralas o transfiérelas a otro mesero antes de cerrar tu caja.

---

## 4. Cocina (KITCHEN)

### 4.1 Pantalla de cocina (KDS)

Al iniciar sesión verás la cola de pedidos organizada por mesa. Cada ítem pasa por estos estados,
en este orden estricto:

```
PENDING  →  PREPARING  →  READY  →  DELIVERED
```

- **PENDING** — el mesero o comensal acaba de enviar el pedido a cocina.
- **PREPARING** — toca el ítem para marcarlo "en preparación" en cuanto empiezas a cocinarlo.
- **READY** — cuando el platillo está listo para servir.
- **DELIVERED** — cuando el mesero confirma que ya lo llevó a la mesa.

> Un ítem sólo se refleja en la cuenta del cliente una vez que llega a **READY** o **DELIVERED** —
> si un mesero pide una cuenta y un platillo sigue en PENDING/PREPARING, el sistema le avisará
> "No billable items" hasta que avances su estado.

### 4.2 Vista por mesas

También puedes ver la cola agrupada por número de mesa ("Kitchen Display") en vez de por orden de
llegada, útil para coordinar cuando varios platillos de una misma mesa deben salir juntos.

---

## 5. Comensal (CUSTOMER)

### 5.1 Unirse a una mesa

1. Ve a la dirección web del restaurante (o escanea el QR en la mesa) y toca **"Register"** si es
   tu primera visita, o inicia sesión si ya tienes cuenta.
2. En tu página de inicio, toca **"Join a table."**
3. Elige **"Enter code"** e ingresa el código de 5 caracteres que te dio el mesero (una casilla por
   carácter — toca la primera casilla y escribe el código completo).
4. Toca **"Confirm"**. Si el código es válido y la mesa no está llena, quedarás unido a ella y
   verás a los demás comensales que ya se unieron.
   - Si el sistema te dice **"You're already at another table. Leave it before joining a new
     one"**, significa que tu cuenta sigue registrada en una mesa anterior — pide ayuda al mesero
     para cerrar esa sesión antes de unirte a una nueva.

### 5.2 Ver el menú y pedir

1. Desde el menú principal, explora las categorías y toca un platillo para ver su detalle y
   elegir modificadores (tamaño, extras, etc., según lo que tenga configurado el restaurante).
2. Agrega los platillos que quieras; verás un contador flotante en la parte inferior con tu
   selección actual y un botón para ir a tu "comanda" (resumen de pedido).
3. Cuando estés listo, confirma tu pedido para enviarlo a cocina. Antes de confirmar queda como
   **borrador** — puedes seguir agregando o quitando ítems libremente; después de confirmar, ya
   no puedes quitarlo tú mismo (pide ayuda al mesero).

> ⚠️ **Si la pantalla del menú se queda en blanco con un mensaje "Error — Something went wrong"**
> (por ejemplo, tras abrir un enlace compartido por otro comensal de la mesa, o refrescar el
> navegador la primera vez que usas el sistema en ese celular), **no es un problema de tu conexión
> a internet**: es un error conocido del sistema (`E-03` del informe técnico). Solución
> inmediata: vuelve a la página de inicio de la app **y entra al menú desde el botón normal de la
> app** en vez de recargar la página directamente en la pantalla del menú; si el error persiste,
> pide al mesero que revise el estado de tu mesa desde su panel — tu pedido y tu lugar en la mesa
> no se pierden, sólo esa pantalla específica falló al cargar.

> ⚠️ **Si el mesero agregó un platillo general para toda la mesa** (aparece como "Mesa" en la
> cuenta), es posible que el botón para confirmar tu propio pedido te muestre un error (`E-06`).
> Si te pasa, avísale al mesero para que tome tu pedido directamente desde su panel.

### 5.3 Pagar tu parte

1. Cuando el mesero solicita la cuenta, verás tu parte a pagar en la pantalla de "Bill".
2. Si tu restaurante tiene pago digital habilitado, puedes iniciar el pago desde ahí; si no,
   entrega el efectivo al mesero para que registre tu pago.
3. Una vez que todas las partes de la mesa quedan pagadas, la mesa se cierra automáticamente.

---

## 6. Preguntas frecuentes y solución de problemas

**"Inicié sesión y no veo nada."**
Es un comportamiento conocido (§1.2) — toca cualquier ícono del menú flotante inferior.

**"El PIN rápido no me deja entrar."**
Después de 5 intentos fallidos, el PIN se bloquea 15 minutos por seguridad. Usa tu correo y
contraseña mientras tanto, o espera.

**"Cobré dos veces por error a un cliente."**
Repórtalo al Administrador de inmediato — se puede reembolsar desde el panel de pagos de esa
cuenta, pero hazlo antes de cerrar tu turno de caja para que el arqueo cuadre correctamente ese
mismo día.

**"El total de la cuenta no coincide con lo que veía antes de cobrar."**
Actualmente el sistema no aplica el impuesto configurado a la factura final (§2.3, `E-05`) — el
total que efectivamente se cobra es la suma de los platillos sin impuesto añadido, sin importar
lo que muestre la vista previa. Ten esto presente al informar precios al cliente hasta que se
corrija.

**"Un comensal dice que no puede confirmar su pedido / no puede quitar un ítem."**
Muy probablemente hay un ítem "Mesa" (agregado por un mesero sin asignarlo a nadie) en esa cuenta
— ver advertencias en §3.3 y §5.2. Tómale el pedido manualmente desde el panel del mesero mientras
se corrige.

**"Alguien más canceló o modificó una mesa que no le correspondía."**
Repórtalo al Administrador — actualmente el sistema no impide que otro mesero toque una mesa que
no es suya (`E-08`, `E-09` del informe técnico); es un tema de acuerdo entre el equipo mientras se
corrige a nivel de sistema.

**¿Dónde reporto un problema que no está en esta lista?**
Contacta al equipo técnico con: qué estabas haciendo, qué esperabas que pasara, y qué pasó en
realidad. Si es posible, incluye el nombre exacto de la mesa/pedido/empleado involucrado y la hora
aproximada — acelera muchísimo el diagnóstico.
