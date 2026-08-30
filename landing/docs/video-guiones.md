# Guiones para los videos de `/info/videos`

Material de producción para grabar los 6 videos que aparecen en la página
**Info → Videos de Ember**. No se publica en el sitio.

La página (`src/pages/info/videos.astro`) ya tiene las 6 tarjetas con título y
descripción (claves `videos.1..6` en `src/i18n/ui.ts`). Cada tarjeta muestra
"Próximamente" hasta que le cargues una URL de embed — ver **Dónde subirlos** al
final.

---

## Antes de grabar

- **Resolución:** 1920×1080, grabá solo la ventana del navegador o la pantalla de
  cocina, no todo el escritorio. Ocultá la barra de marcadores y pestañas de más.
- **Datos:** usá un restaurante de demo con datos inventados. Nunca muestres
  nombres, correos, teléfonos ni montos reales de un cliente.
- **Ritmo:** cada acción con una pausa de ~1 s antes y después. Movés el mouse
  despacio; nada de clics frenéticos.
- **Duración objetivo:** 60–120 s por video. Si algo se va de largo, cortalo en la
  edición.
- **Audio:** narración clara o, si preferís, sin voz y con subtítulos/carteles.
  Sin música de fondo con volumen alto.
- **Zoom:** subí el zoom del navegador a 110–125 % para que el texto se lea en
  móvil.
- **Continuidad:** grabá el video 1 (recorrido general) al final, cuando ya tengas
  clips de los demás para reaprovechar.

Cada guion tiene **Pantalla** (qué se ve / qué hacés) y **Narración** (lo que
decís, aproximado).

---

## 1 · Recorrido general

**Objetivo:** que en un minuto se entienda qué es Ember y cómo encajan los
módulos. **Duración:** ~75 s.

**Pantalla**
1. Panel de administración recién abierto.
2. Abrís la vista de piso: se ven las mesas con estado.
3. Cambiás a la pantalla de cocina (KDS) con un par de pedidos en distintos
   estados.
4. Volvés al panel de una mesa y mostrás el cobro / división de cuenta.
5. Terminás en el panel de analítica con un filtro por semana.

**Narración**
> "Ember es una plataforma para gestionar un restaurante en tiempo real. Desde un
> solo panel ves el piso —todas las mesas y su estado—, la cocina, donde cada
> pedido avanza por estados controlados, y el cobro, con división de cuentas y
> cierre de caja. Y para el administrador, analítica de ventas y desempeño por
> producto, mesa y mesero. Todo lo que pasa en una pantalla se refleja al
> instante en las demás."

**Notas:** no entres en detalle de ningún módulo, esto es el índice.

---

## 2 · Alta de restaurante

**Objetivo:** mostrar que poner Ember a andar es un asistente guiado, no una
instalación. **Duración:** ~90 s.

**Pantalla**
1. Pantalla de registro / primer ingreso.
2. Paso 1 del asistente: datos del negocio (nombre, dirección).
3. Paso 2: cargás 3–4 mesas del salón.
4. Paso 3: creás una categoría (p. ej. "Bebidas"), un plato con precio y un grupo
   de modificadores simple.
5. Fin del asistente → entrás al panel de administración ya poblado.

**Narración**
> "Al crear la cuenta, un asistente te guía paso a paso. Primero los datos del
> local. Después cargás las mesas de tu salón. Y por último el catálogo:
> categorías, platos con su precio y, si los usás, grupos de modificadores como
> el término de la carne o los extras. Cuando terminás, ya entrás a un panel con
> tu restaurante listo para operar."

**Notas:** mostrá que se puede volver atrás entre pasos.

---

## 3 · Servicio de mesa completo

**Objetivo:** el flujo de punta a punta, el corazón del producto. Idealmente con
**dos dispositivos**: teléfono (comensal) y computadora (mesero/cocina).
**Duración:** ~120 s.

**Pantalla**
1. En el teléfono: escaneás el QR de la mesa (o tipeás el código de 5
   caracteres) y entrás al carrito.
2. Agregás 2–3 platos desde el teléfono. En la computadora se ve el carrito
   actualizarse en vivo.
3. Confirmás el pedido desde el teléfono.
4. En la pantalla de cocina aparece el pedido en "Pendiente"; lo movés a "En
   preparación" y a "Listo".
5. En el panel del mesero: agregás un ítem manual, dividís la cuenta y registrás
   el cobro.
6. Cerrás la mesa: vuelve a "libre" en el piso.

**Narración**
> "El comensal se une a la mesa con el QR, sin instalar nada. Agrega sus platos y
> todos en la mesa ven el mismo carrito en tiempo real. Cuando confirma, el
> pedido entra directo a la cocina y avanza por estados: pendiente, en
> preparación, listo. El mesero puede sumar algo a mano, dividir la cuenta y
> cobrar. Al cerrar, la mesa queda libre en el piso."

**Notas:** este es el clip más importante; si solo grabás uno bien, que sea este.

---

## 4 · Cocina en vivo (KDS)

**Objetivo:** mostrar la pantalla de comandas trabajando bajo carga.
**Duración:** ~60 s.

**Pantalla**
1. Pantalla de cocina con 4–5 pedidos en distintos estados.
2. Entra un pedido nuevo (disparalo desde otro dispositivo) → aparece en
   "Pendiente".
3. Movés tarjetas: Pendiente → En preparación → Listo → Entregado.
4. Mostrás un pedido "demorado" resaltado.
5. Si tenés dos estaciones configuradas, mostrás que cada pantalla ve solo lo
   suyo.

**Narración**
> "Cada pedido confirmado llega acá y la cocina lo mueve por estados con un
> toque. El cambio se ve al instante en el panel del mesero. Los pedidos que
> llevan mucho tiempo sin avanzar se resaltan solos, para que no se pierda
> ninguno. Podés tener una pantalla por estación, cada una mostrando solo lo que
> le toca."

---

## 5 · Cierre de caja

**Objetivo:** mostrar el arqueo y el control de turno vencido. **Duración:**
~75 s.

**Pantalla**
1. Panel de caja con movimientos y cobros del turno.
2. Abrís el cierre de caja: Ember muestra el total esperado.
3. Ingresás el efectivo contado → muestra la diferencia.
4. Confirmás el cierre.
5. (Opcional) Mostrás el aviso de "turno vencido" y el botón para prolongar.

**Narración**
> "Al final del turno se hace el cierre de caja. Ember calcula cuánto debería
> haber según los cobros, vos ingresás lo que contaste y te muestra la
> diferencia. Además, cada turno tiene un horario de cierre: si se pasa, Ember
> avisa y te pide cerrarlo o prolongarlo antes de seguir cobrando en efectivo."

---

## 6 · Analítica para administradores

**Objetivo:** mostrar qué decisiones habilita la analítica. **Duración:** ~75 s.

**Pantalla**
1. Panel de analítica con el filtro en "Semana".
2. Cambiás el filtro a Día / Mes / Año y las cifras se actualizan.
3. Mostrás ventas totales y ticket promedio.
4. Bajás a "productos más vendidos".
5. Abrís el desempeño por mesa y por mesero.

**Narración**
> "El administrador ve ventas, ticket promedio y los productos que más salen, con
> filtros por día, semana, mes o año. Y el desglose por mesa y por mesero: qué
> mesa rota más, cómo rinde cada persona del equipo. Los números se actualizan a
> medida que entran los pagos."

---

## Dónde subir los videos

**No van en este repositorio.** Son archivos pesados, sin CDN, y Cloudflare Pages
tiene límite de tamaño de deploy. La página los embebe por URL justamente para
que vivan afuera.

### Opción recomendada: YouTube

1. Creá (una sola vez) un canal de YouTube para Vanter / Ember.
2. Subí cada video. Elegí visibilidad:
   - **Público:** aparece en tu canal y suma otra superficie de descubrimiento.
   - **No listado:** no sale en búsquedas ni sugerencias, pero se puede embeber y
     compartir por enlace. Buena opción si todavía no querés presencia de canal.
3. Copiá el **ID** del video: es lo que sigue a `watch?v=` en la URL
   (`https://www.youtube.com/watch?v=XXXXXXXXXXX` → `XXXXXXXXXXX`).
4. Pegá ese ID en `src/pages/info/videos.astro`, en el array `ids`, en la posición
   del video correspondiente:
   ```ts
   const ids: (string | null)[] = [
     'XXXXXXXXXXX', // 1 · Recorrido general
     null,          // 2 · Alta de restaurante
     // ...
   ];
   ```
   También se acepta pegar la URL completa de YouTube o de Vimeo en vez del ID.
5. `git commit` de ese cambio y redeploy. La tarjeta deja de mostrar
   "Próximamente": muestra la miniatura del video con un botón de play y, al hacer
   clic, se abre en un modal. El embed se carga en modo `youtube-nocookie` y recién
   cuando el usuario hace clic.

### Alternativas

- **Vimeo:** ya soportado por la página. La URL de embed es
  `https://player.vimeo.com/video/XXXXXXXX`. Menos alcance que YouTube, sin
  anuncios, plan gratuito con límites de subida.
- **Cloudflare Stream:** hosting de video propio, sin marca de terceros, integrado
  con la infra que ya usás (Cloudflare). Es **pago** (aprox. US$5 por cada 1000
  minutos almacenados + entrega) y hay que ajustar el `<iframe>` a su player.
  Solo tiene sentido si te molesta la marca de YouTube/Vimeo.

### Si más adelante querés SEO de video

Cuando los videos estén publicados, se puede agregar `VideoObject` en el JSON-LD
de esa página (`thumbnailUrl`, `uploadDate`, `contentUrl`/`embedUrl`) para que
Google los muestre como resultado de video. Es una tarea aparte, no hace falta
para publicarlos.
