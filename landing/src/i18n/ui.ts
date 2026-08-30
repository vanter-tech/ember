export const defaultLang = 'es' as const;
export type Lang = 'es' | 'en';

export const ui: Record<Lang, Record<string, string>> = {
	es: {
		// --- Nav ---
		'nav.features': 'Funcionalidades',
		'nav.pricing': 'Precios',
		'nav.info': 'Info',
		'nav.contact': 'Contacto',
		'nav.login': 'Iniciar sesión',
		'nav.register': 'Registrarme',
		'nav.toggleTheme': 'Cambiar tema',
		'nav.openMenu': 'Abrir menú',
		'nav.closeMenu': 'Cerrar menú',
		'nav.langLabel': 'Idioma',

		// --- Hero ---
		'hero.eyebrow': 'Gestión de restaurantes en tiempo real',
		'hero.title.prefix': 'Tu restaurante,',
		'hero.title.accent': 'sincronizado',
		'hero.lede':
			'Carrito colaborativo, comandas en cocina, gestión de mesas y analítica en un solo panel. Sin fricción, sin retrasos.',
		'hero.cta.register': 'Registrarme',
		'hero.cta.plans': 'Ver planes',
		'hero.bar.1': 'Carrito colaborativo por QR',
		'hero.bar.2': 'Comandas de cocina (KDS)',
		'hero.bar.3': 'División de cuentas y caja',
		'hero.bar.4': 'Analítica por período',
		'hero.bar.5': 'Multi-restaurante',
		'hero.card.title': 'Mesa 8 · lista',
		'hero.card.sub': 'Cocina la marcó hace 2 s',
		'hero.shotAlt': 'Panel de Ember con la vista de mesas del mesero en tiempo real',

		// --- Compare ---
		'cmp.eyebrow': 'Ember vs. lo de siempre',
		'cmp.title': 'El mismo servicio, sin los cuellos de botella',
		'cmp.col.voice': 'Comanda a voz o papel',
		'cmp.col.pos': 'POS tradicional',
		'cmp.col.ember': 'Ember',
		'cmp.r1.label': 'Toma de pedido',
		'cmp.r1.voice': 'El mesero anota y camina cada pedido a la cocina',
		'cmp.r1.pos': 'El mesero tipea todo en una terminal fija',
		'cmp.r1.ember': 'El comensal pide desde su teléfono; la cocina lo ve al instante',
		'cmp.r2.label': 'Dividir la cuenta',
		'cmp.r2.voice': 'A mano, lento y con errores',
		'cmp.r2.pos': 'Función limitada, siempre la hace el mesero',
		'cmp.r2.ember': 'Cada comensal confirma y paga su parte desde la mesa',
		'cmp.r3.label': 'Estado de la cocina',
		'cmp.r3.voice': '«¿Ya sale la 8?» a los gritos',
		'cmp.r3.pos': 'Ticket impreso, sin seguimiento',
		'cmp.r3.ember': 'Pantalla KDS con estados y alertas de demora',
		'cmp.r4.label': 'Puesta en marcha',
		'cmp.r4.voice': 'Sin costo, pero todo en la cabeza del personal',
		'cmp.r4.pos': 'Instalación, hardware propietario y capacitación',
		'cmp.r4.ember': 'Creás la cuenta, cargás el menú y corre en el navegador',

		// --- Trust ---
		'trust.eyebrow': 'Sin letra chica',
		'trust.title': 'Lo que podés esperar de Ember',
		'trust.1': 'Plan Free real: sin tarjeta, sin límite de tiempo',
		'trust.2': 'Cancelás cuando quieras, sin penalidad',
		'trust.3': 'Los datos de cada restaurante quedan aislados',
		'trust.4': 'Funciona con tus impresoras de red o de Windows',
		'trust.5': 'El comensal no instala nada: entra por el navegador',
		'trust.closing': 'Detrás de Ember está Vanter, desde Managua, Nicaragua.',
		'trust.contact': 'Hablar con el equipo',

		// --- How it works ---
		'how.eyebrow': 'Cómo funciona',
		'how.title': 'Tres pasos, del QR a la caja',
		'how.1.title': 'El comensal se une',
		'how.1.body': 'Escanea el QR de la mesa o entra con un código de 5 caracteres. Sin instalar nada.',
		'how.2.title': 'Piden desde la mesa',
		'how.2.body':
			'Todos ven el mismo carrito compartido en tiempo real y cada uno confirma y paga su parte.',
		'how.3.title': 'Cocina y caja, en vivo',
		'how.3.body':
			'El pedido llega a la pantalla de cocina y al panel del mesero al instante: preparación, cobro y cierre de caja.',

		// --- Features teaser ---
		'ftease.eyebrow': 'Funcionalidades',
		'ftease.title': 'Todo el servicio en un solo panel',
		'ftease.link': 'Ver todas las funcionalidades',

		// --- Why Ember ---
		'why.eyebrow': 'Por qué Ember',
		'why.title': 'Pensado para el ritmo de un restaurante',
		'why.1.title': 'En tiempo real',
		'why.1.body':
			'Mesas, comandas y pagos se sincronizan al instante por WebSocket entre todos los dispositivos.',
		'why.2.title': 'Sin apps para el comensal',
		'why.2.body': 'Se une con el QR desde el navegador del teléfono. Nada que descargar ni instalar.',
		'why.3.title': 'Un solo panel',
		'why.3.body':
			'Carrito, cocina, piso, cobro y analítica en la misma plataforma, sin integraciones frágiles.',
		'why.4.title': 'Multi-restaurante',
		'why.4.body': 'Cada local aislado y configurable; sumás sucursales sin cambiar de sistema.',

		// --- FAQ ---
		'faq.eyebrow': 'Preguntas frecuentes',
		'faq.title': 'Antes de empezar',
		'faq.1.q': '¿El comensal tiene que instalar una app?',
		'faq.1.a':
			'No. Se une a la mesa escaneando el QR o ingresando un código de 5 caracteres; todo corre en el navegador del teléfono.',
		'faq.2.q': '¿Sirve para un solo local?',
		'faq.2.a':
			'Sí. Ember funciona igual para un restaurante o para una cadena; cada local queda aislado y podés sumar sucursales cuando quieras.',
		'faq.3.q': '¿Cómo pagan los comensales?',
		'faq.3.a':
			'Cada participante confirma y paga su propia parte del carrito compartido. El mesero también puede cobrar la mesa completa, dividir o unir cuentas y hacer el cierre de caja por turno.',
		'faq.4.q': '¿Necesito una impresora especial?',
		'faq.4.a':
			'El agente de impresión de Ember conecta impresoras de red o de cola de Windows y les manda comandas y recibos automáticamente. No hace falta hardware propietario.',
		'faq.5.q': '¿Lo puedo probar gratis?',
		'faq.5.a':
			'Sí, el plan Free no tiene costo ni tarjeta. Podés comparar todos los planes en la página de precios.',
		'faq.6.q': '¿Cómo empiezo?',
		'faq.6.a':
			'Creás la cuenta y el asistente de alta te guía para cargar mesas y catálogo. Si preferís una demo guiada, escribinos.',
		'faq.link.plans': 'Ver planes',
		'faq.link.contact': 'Contacto',

		// --- CTA ---
		'cta.eyebrow': 'Sin fricción, sin retrasos',
		'cta.title': 'Pon tu restaurante en tiempo real',
		'cta.lede':
			'Únete a los restaurantes que ya gestionan mesas, comandas y analítica desde un solo panel.',
		'cta.register': 'Registrarme gratis',
		'cta.plans': 'o mirá los planes →',

		// --- Footer ---
		'footer.tagline':
			'Gestión de restaurantes en tiempo real: comandas, mesas y analítica en un solo panel.',
		'footer.legal': 'Legal',
		'footer.privacy': 'Política de privacidad',
		'footer.terms': 'Términos de servicio',
		'footer.contact': 'Contacto',
		'footer.rights': 'Todos los derechos reservados.',

		// --- Cookie banner ---
		'cookie.text': 'Usamos cookies esenciales para el funcionamiento del sitio. Consulta nuestra',
		'cookie.link': 'Política de Privacidad',
		'cookie.accept': 'Aceptar',

		// --- Sticky mobile CTA ---
		'sticky.login': 'Iniciar sesión',
		'sticky.register': 'Registrarme',

		// --- Features (data) ---
		'feat.cart.title': 'Carrito colaborativo',
		'feat.cart.desc':
			'Los comensales se unen con un código QR y agregan platos a un carrito compartido en tiempo real, sin apps que instalar.',
		'feat.cart.p1': 'Unirse por QR o código de 5 caracteres',
		'feat.cart.p2': 'Carrito compartido en vivo entre todos los comensales',
		'feat.cart.p3': 'Cada participante confirma y paga su propia parte',
		'feat.cart.p4': 'Sin instalación: corre en el navegador del teléfono',
		'feat.kds.title': 'Comandas en cocina (KDS)',
		'feat.kds.desc':
			'Cada pedido avanza por estados controlados — pendiente, en preparación, listo, entregado — visibles al instante en pantalla.',
		'feat.kds.p1': 'Cola de pedidos en tiempo real por estación',
		'feat.kds.p2': 'Transiciones Pendiente → En preparación → Listo → Entregado',
		'feat.kds.p3': 'Sincronización instantánea vía WebSocket',
		'feat.kds.p4': 'Los pedidos demorados se destacan solos',
		'feat.floor.title': 'Gestión de piso y meseros',
		'feat.floor.desc':
			'Asignación de mesas, altas manuales, división de cuentas y cierre de caja por turno, todo desde un mismo panel.',
		'feat.floor.p1': 'Estado de todas las mesas del salón en un mapa',
		'feat.floor.p2': 'Abrir, asignar y cerrar sesiones de mesa',
		'feat.floor.p3': 'Altas manuales de ítems y división o unión de cuentas',
		'feat.floor.p4': 'Cierre de caja por turno con arqueo',
		'feat.analytics.title': 'Analítica para administradores',
		'feat.analytics.desc':
			'Métricas de ventas, ticket promedio y desempeño por producto o mesa, filtrables por día, semana, mes o año.',
		'feat.analytics.p1': 'Ventas, ticket promedio e ítems más vendidos',
		'feat.analytics.p2': 'Filtros por día, semana, mes y año',
		'feat.analytics.p3': 'Desempeño por producto y por mesa',
		'feat.analytics.p4': 'Gestión de empleados, roles y configuración del restaurante',
		'feat.shotAlt': 'Vista de Ember:',

		// --- Plans (data) ---
		'plan.free.tagline': 'Para probar Ember sin compromiso.',
		'plan.free.f1': '1 mesa activa',
		'plan.free.f2': 'Carrito colaborativo',
		'plan.free.f3': 'Comandas en cocina (KDS)',
		'plan.free.f4': 'Soporte por comunidad',
		'plan.free.cta': 'Empezar gratis',
		'plan.starter.tagline': 'Para sodas, comedores y cafés.',
		'plan.starter.f1': 'Hasta 10 mesas',
		'plan.starter.f2': 'KDS y gestión de piso',
		'plan.starter.f3': 'División de cuentas',
		'plan.starter.f4': 'Soporte por correo',
		'plan.starter.cta': 'Registrarme',
		'plan.pro.tagline': 'Para restaurantes con volumen.',
		'plan.pro.f1': 'Mesas ilimitadas',
		'plan.pro.f2': 'Analítica avanzada',
		'plan.pro.f3': 'Múltiples meseros y roles',
		'plan.pro.f4': 'Soporte prioritario',
		'plan.pro.cta': 'Registrarme',
		'plan.ent.tagline': 'Para cadenas y franquicias.',
		'plan.ent.f1': 'Multi-sucursal',
		'plan.ent.f2': 'SLA dedicado',
		'plan.ent.f3': 'Integraciones a medida',
		'plan.ent.f4': 'Gerente de cuenta',
		'plan.ent.cta': 'Hablar con ventas',
		'plan.priceCustom': 'A medida',
		'plan.perMonth': '/mes',
		'plan.perYear': '/año',
		'plan.mostPopular': 'Más popular',

		// --- Pricing table ---
		'ptable.heading': 'Comparación de planes',
		'ptable.g.floor': 'Operación en piso',
		'ptable.g.analytics': 'Analítica',
		'ptable.g.team': 'Equipo y acceso',
		'ptable.g.scale': 'Escala y soporte',
		'ptable.r.tables': 'Mesas activas',
		'ptable.r.cart': 'Carrito colaborativo (QR / código)',
		'ptable.r.kds': 'Comandas en cocina (KDS)',
		'ptable.r.floor': 'Gestión de piso y meseros',
		'ptable.r.split': 'División y unión de cuentas',
		'ptable.r.cashclose': 'Cierre de caja por turno',
		'ptable.r.print': 'Impresión de comandas y recibos',
		'ptable.r.rooms': 'Múltiples salones / áreas',
		'ptable.r.metrics': 'Métricas de ventas básicas',
		'ptable.r.periodfilters': 'Filtros por día / semana / mes / año',
		'ptable.r.advanced': 'Analítica avanzada (ticket promedio, producto, mesa)',
		'ptable.r.export': 'Exportación de reportes',
		'ptable.r.roles': 'Roles Mesero / Cocina / Admin',
		'ptable.r.staff': 'Gestión de empleados',
		'ptable.r.multiwaiter': 'Múltiples meseros simultáneos',
		'ptable.r.branding': 'Configuración de marca (branding)',
		'ptable.r.multibranch': 'Multi-sucursal',
		'ptable.r.integrations': 'Integraciones a medida',
		'ptable.r.sla': 'SLA dedicado',
		'ptable.r.am': 'Gerente de cuenta',
		'ptable.r.support': 'Soporte',
		'ptable.v.unlimited': 'Ilimitadas',
		'ptable.v.community': 'Comunidad',
		'ptable.v.email': 'Correo',
		'ptable.v.priority': 'Prioritario',
		'ptable.v.dedicated': 'Dedicado 24/7',
		'ptable.included': 'Incluido',
		'ptable.notIncluded': 'No incluido',

		// --- Page: index (SEO) ---
		'seo.home.title': 'Ember — Plataforma de gestión de restaurantes',
		'seo.home.desc':
			'Pedido colaborativo en tiempo real, pantalla de cocina, gestión de piso y analítica para restaurantes modernos.',

		// --- Page: funcionalidades ---
		'fpage.title': 'Funcionalidades — Ember',
		'fpage.desc':
			'Carrito colaborativo, comandas de cocina (KDS), gestión de piso y analítica: todo lo que hace Ember.',
		'fpage.eyebrow': 'Funcionalidades',
		'fpage.h1': 'Hecho para el flujo real de un restaurante',
		'fpage.lede':
			'Cuatro módulos que trabajan sobre el mismo panel en tiempo real, desde que el comensal escanea el QR hasta el cierre de caja.',

		// --- Page: planes ---
		'ppage.title': 'Planes y precios — Ember',
		'ppage.desc':
			'Compará los planes Free, Starter, Pro y Enterprise de Ember: mesas, KDS, analítica, roles y soporte.',
		'ppage.eyebrow': 'Planes y precios',
		'ppage.h1': 'Elegí el plan que va con tu operación',
		'ppage.lede':
			'Todos los planes incluyen el carrito colaborativo y las comandas en cocina. Empezás gratis, sin tarjeta, y cambiás de plan cuando quieras, sin migrar de sistema.',
		'ppage.section2': 'Qué incluye cada plan',
		'ppage.section2sub': 'Detalle completo de funcionalidades por plan.',
		'ppage.fineprint':
			'Precios en USD, por restaurante, sin impuestos. Con plan anual pagás 10 meses y usás 12. Enterprise se cotiza según la cantidad de sucursales y el volumen.',
		'ppage.billing.label': 'Ciclo de facturación',
		'ppage.billing.monthly': 'Mensual',
		'ppage.billing.annual': 'Anual',
		'ppage.billing.save': '2 meses gratis',

		// --- Page: contacto ---
		'cpage.title': 'Contacto — Ember',
		'cpage.desc':
			'Cómo ponerte en contacto con el equipo de Ember: correo, oficina y horario de atención.',
		'cpage.eyebrow': 'Hablemos',
		'cpage.h1': 'Estamos para ayudarte a poner tu restaurante en Ember',
		'cpage.lede':
			'Escribinos y coordinamos una demo o armamos un plan a la medida de tu operación.',
		'cpage.mail.title': 'Correo',
		'cpage.mail.body': 'Para demos, planes a medida y soporte. Respondemos dentro de un día hábil.',
		'cpage.office.title': 'Oficina',
		'cpage.hours.title': 'Horario',
		'cpage.hours.body': 'Lunes a viernes<br />8:00 a 18:00 (GMT−6)',
		'cpage.demo.text':
			'¿Preferís vernos en acción? Coordinamos una demo de 20 minutos por videollamada.',
		'cpage.demo.cta': 'Pedir una demo',

		// --- Info section ---
		'info.sidebar': 'Información',
		'info.nav.overview': 'Resumen',
		'info.nav.manual': 'Manual de usuario',
		'info.nav.videos': 'Videos de Ember',
		'info.title': 'Información — Ember',
		'info.desc': 'Manual de usuario y videos de Ember: aprendé a usar la plataforma.',
		'info.h1': 'Aprendé a usar Ember',
		'info.lede':
			'Documentación y material en video para poner tu restaurante en marcha y sacarle todo el provecho a la plataforma.',
		'info.card.manual.title': 'Manual de usuario',
		'info.card.manual.body':
			'Cómo usar Ember paso a paso: primeros pasos, roles, piso y mesas, cocina, cobro y analítica.',
		'info.card.videos.title': 'Videos de Ember',
		'info.card.videos.body':
			'Recorridos en video de la plataforma funcionando en un servicio real.',

		'manual.title': 'Manual de usuario — Ember',
		'manual.desc':
			'Guía de uso de Ember: primeros pasos, roles, piso, cocina, cobro y analítica.',
		'manual.h1': 'Manual de usuario',
		'manual.lede':
			'Cómo funciona cada módulo de Ember: qué hace, quién lo usa y el flujo típico de un servicio. Usá el índice para saltar al módulo que te interese.',
		'manual.toc': 'En esta página',
		'manual.s1.title': 'Primeros pasos',
		'manual.s1.body':
			'Al crear la cuenta de tu restaurante, un asistente de alta te guía para cargar los datos del negocio, las mesas del salón y el catálogo inicial: categorías, platos con precio y, si los usás, grupos de modificadores (por ejemplo «término de la carne» o «extras»). Cuando terminás entrás al panel de administración, desde donde se configura y se monitorea todo. El personal de mesa y de cocina usa la misma dirección web para iniciar sesión con las credenciales que le crea el admin.',
		'manual.s2.title': 'Roles y accesos',
		'manual.s2.body':
			'Ember tiene cuatro roles. El Admin configura el catálogo y la operación, gestiona al personal y ve la analítica. El Mesero trabaja sobre el salón: abre y cierra mesas, suma ítems, cobra y hace el cierre de caja. Cocina ve solo la pantalla de comandas. El Comensal no tiene cuenta: entra a la mesa por QR o código y solo ve su carrito. El admin da de alta a cada persona y le asigna su rol; cada quien ve únicamente lo que su rol permite.',
		'manual.s3.title': 'Piso y mesas',
		'manual.s3.body':
			'La vista de piso es un mapa de todas las mesas del salón con su estado en vivo: libre u ocupada, y cuántos comensales hay sentados. Desde ahí el mesero abre la sesión de una mesa, la asigna, une o separa mesas, y la libera al cerrar la cuenta. Todo se sincroniza al instante entre los dispositivos, así que dos meseros nunca ven estados distintos de la misma mesa.',
		'manual.s4.title': 'Carrito colaborativo',
		'manual.s4.body':
			'El comensal se une a la sesión de su mesa escaneando el QR o ingresando un código de 5 caracteres en el navegador del teléfono, sin instalar ninguna app. Todos los que están en la mesa comparten el mismo carrito y lo ven actualizarse en tiempo real a medida que cada uno agrega platos. Cada participante confirma y paga su propia parte, o el mesero cobra la mesa completa. Al confirmar, el pedido pasa directo a la cocina.',
		'manual.s5.title': 'Comandas en cocina (KDS)',
		'manual.s5.body':
			'Cada pedido confirmado aparece en la pantalla de cocina y avanza por estados controlados: Pendiente → En preparación → Listo → Entregado. La cocina toca cada tarjeta para moverla de estado y el cambio se refleja al instante en el panel del mesero. Los pedidos que llevan demasiado tiempo sin avanzar se destacan solos. Podés tener una pantalla por estación —por ejemplo cocina y barra— mostrando solo lo que le corresponde a cada una.',
		'manual.s6.title': 'Cuentas y cobro',
		'manual.s6.body':
			'Desde el panel de una mesa el mesero ve todo lo pedido, suma ítems manuales que no pasaron por el carrito, y divide o une cuentas según cómo quieran pagar los comensales. Registra cada cobro indicando el medio de pago. Al terminar el turno se hace el cierre de caja con arqueo: Ember compara lo que debería haber en caja contra lo que el mesero cuenta. El turno tiene un horario de cierre; si se pasa, Ember avisa y pide cerrarlo o prolongarlo antes de seguir cobrando en efectivo.',
		'manual.s7.title': 'Analítica',
		'manual.s7.body':
			'El panel de analítica del admin muestra ventas totales, ticket promedio y los productos más vendidos, con filtros por día, semana, mes y año. Además desglosa el desempeño por producto, por mesa y por mesero, para ver qué se vende, qué mesa rota más y cómo rinde cada persona del equipo. Los datos se actualizan a medida que entran los pagos.',
		'manual.s8.title': 'Impresión de comandas y recibos',
		'manual.s8.body':
			'Ember imprime comandas para la cocina y recibos para el cliente a través de un agente de impresión que corre en una computadora del local. El agente conecta impresoras de red o de cola de Windows y recibe los trabajos automáticamente cuando se confirma un pedido o se cobra una mesa. Soporta impresoras térmicas (ESC/POS) y también impresoras comunes con driver, para locales que no tienen una térmica dedicada. Se configura una sola vez, asignando cada impresora a un rol (cocina, barra, caja).',
		'manual.s9.title': 'Configuración del restaurante',
		'manual.s9.body':
			'En Configuración el admin ajusta la marca que ven los comensales (logo y colores), el formato del ticket, el catálogo completo (categorías, platos, precios y grupos de modificadores), las mesas y salones, el personal y sus roles, las impresoras y los datos del negocio. Cada pantalla de configuración tiene un botón de ayuda que abre un recorrido guiado de esa sección.',
		'manual.s10.title': 'Solución de problemas',
		'manual.s10.body':
			'El comensal no puede unirse por QR: verificá que la mesa tenga una sesión abierta y que el teléfono tenga internet; siempre podés dictarle el código de 5 caracteres de esa mesa.\nLa impresora no imprime: confirmá que la computadora con el agente de impresión esté encendida y conectada, que la impresora tenga papel y que esté asignada al rol correcto en Configuración.\nUn pedido no aparece en cocina: solo pasan los pedidos confirmados; lo que queda en el carrito sin confirmar no se envía. Revisá también que la pantalla esté en la estación correcta.\n«Turno de caja vencido»: el turno pasó su horario de cierre. Hacé el cierre de caja con arqueo, o prolongá el turno si el servicio sigue activo.\nNo ves un módulo o una acción: casi siempre es porque tu rol no tiene acceso a esa parte. Pedile al admin que revise tu rol.',

		'videos.title': 'Videos de Ember — Ember',
		'videos.desc':
			'Videos de Ember funcionando: recorridos de la plataforma en un servicio real.',
		'videos.h1': 'Videos de Ember',
		'videos.lede':
			'Recorridos cortos de Ember funcionando en un servicio real. Los vamos publicando a medida que están listos; mientras tanto, el manual cubre cada módulo por escrito.',
		'videos.soon': 'Próximamente',
		'videos.play': 'Reproducir',
		'videos.close': 'Cerrar',
		'videos.1.title': 'Recorrido general',
		'videos.1.body': 'De un vistazo: piso, cocina, cobro y analítica, para ver cómo encajan los módulos.',
		'videos.2.title': 'Alta de restaurante',
		'videos.2.body':
			'El asistente de alta paso a paso: datos del negocio, mesas y carga del catálogo con precios y modificadores.',
		'videos.3.title': 'Servicio de mesa completo',
		'videos.3.body':
			'Un servicio de principio a fin: el comensal se une por QR, pide, la cocina prepara y el mesero cierra la cuenta.',
		'videos.4.title': 'Cocina en vivo (KDS)',
		'videos.4.body':
			'La pantalla de comandas en acción: pedidos entrando, cambios de estado y cómo se marcan los que se demoran.',
		'videos.5.title': 'Cierre de caja',
		'videos.5.body':
			'El cierre de turno con arqueo: qué muestra Ember, cómo se cuadra y qué pasa si el turno queda vencido.',
		'videos.6.title': 'Analítica para administradores',
		'videos.6.body':
			'El panel del admin: ventas, ticket promedio y desempeño por producto, mesa y mesero, con filtros por período.',

		// --- Legal: privacy ---
		'legal.eyebrow': 'Legal',
		'legal.updated': 'Última actualización:',
		'privacy.title': 'Política de privacidad — Ember',
		'privacy.desc':
			'Cómo Vanter recolecta, usa y protege los datos personales de quienes usan Ember.',
		'privacy.h1': 'Política de privacidad',
		'privacy.date': '29 de agosto de 2026',
		'privacy.s1.title': '1. Responsable del tratamiento',
		'privacy.s1.body':
			'Vanter, con domicilio en Managua, Nicaragua, es la responsable del tratamiento de los datos personales recolectados a través de Ember y este sitio.',
		'privacy.s2.title': '2. Datos que recolectamos',
		'privacy.s2.body':
			'Recolectamos los datos que un restaurante y su personal ingresan al usar Ember (nombre, correo electrónico, rol, datos de facturación del negocio) y los datos que un comensal ingresa al unirse a una sesión de mesa (nombre visible, pedidos). También registramos datos técnicos básicos de navegación en este sitio (páginas visitadas, tipo de dispositivo) con fines de mejora del producto.',
		'privacy.s3.title': '3. Uso de los datos',
		'privacy.s3.body':
			'Usamos los datos únicamente para prestar el servicio: autenticar usuarios, procesar comandas y pagos, generar analítica para el restaurante que administra su cuenta, y responder consultas comerciales o de soporte. No vendemos datos personales a terceros.',
		'privacy.s4.title': '4. Cookies',
		'privacy.s4.body':
			'Este sitio usa únicamente cookies estrictamente necesarias para su funcionamiento. Para medir el tráfico usamos una herramienta de analítica sin cookies y sin fines publicitarios, que no rastrea entre sitios ni identifica personas. Podés gestionar cualquier cookie desde tu navegador en cualquier momento.',
		'privacy.s5.title': '5. Conservación',
		'privacy.s5.body':
			'Conservamos los datos mientras la cuenta del restaurante permanezca activa y durante el plazo adicional exigido por obligaciones legales o contractuales. Un comensal que participa en una sesión de mesa no requiere registro previo; sus datos de sesión se conservan según las políticas de retención del restaurante correspondiente.',
		'privacy.s6.title': '6. Derechos del titular',
		'privacy.s6.body':
			'Podés solicitar acceso, rectificación o eliminación de tus datos personales escribiendo a tofernandoband01@outlook.com. Responderemos dentro de los plazos establecidos por la Ley No. 787, Ley de Protección de Datos Personales de Nicaragua.',
		'privacy.s7.title': '7. Contacto',
		'privacy.s7.body':
			'Para cualquier consulta sobre esta política, escribí a tofernandoband01@outlook.com o dirigite a Vanter, Managua, Nicaragua.',

		// --- Legal: terms ---
		'terms.title': 'Términos de servicio — Ember',
		'terms.desc': 'Condiciones de uso de la plataforma Ember, operada por Vanter.',
		'terms.h1': 'Términos de servicio',
		'terms.date': '29 de agosto de 2026',
		'terms.s1.title': '1. Aceptación de los términos',
		'terms.s1.body':
			'Al crear una cuenta o usar Ember, el restaurante contratante y su personal aceptan estos Términos de Servicio y la Política de privacidad. Si no estás de acuerdo, no debés usar el servicio.',
		'terms.s2.title': '2. Descripción del servicio',
		'terms.s2.body':
			'Ember es una plataforma de gestión de restaurantes ofrecida por Vanter que incluye carrito colaborativo para comensales, comandas de cocina (KDS), gestión de piso/meseros, facturación y analítica administrativa, según el plan contratado.',
		'terms.s3.title': '3. Cuentas y responsabilidad',
		'terms.s3.body':
			'El restaurante es responsable de mantener la confidencialidad de las credenciales de su personal y de toda actividad realizada bajo su cuenta. Vanter no se responsabiliza por accesos no autorizados derivados de un manejo negligente de credenciales.',
		'terms.s4.title': '4. Planes y facturación',
		'terms.s4.body':
			'El acceso a Ember se rige por el plan contratado (FREE, STARTER, PRO o ENTERPRISE). Los cambios de plan, suspensión o cancelación se gestionan según lo acordado comercialmente con Vanter o desde el panel de administración correspondiente.',
		'terms.s5.title': '5. Uso aceptable',
		'terms.s5.body':
			'No está permitido usar Ember para fines ilícitos, interferir con la operación del servicio, intentar acceder a datos de otro restaurante (tenant) sin autorización, o realizar ingeniería inversa sobre la plataforma.',
		'terms.s6.title': '6. Disponibilidad del servicio',
		'terms.s6.body':
			'Vanter realiza esfuerzos razonables para mantener Ember disponible, pero no garantiza un servicio libre de interrupciones. Se podrán realizar mantenimientos programados o de emergencia con o sin aviso previo según la criticidad.',
		'terms.s7.title': '7. Limitación de responsabilidad',
		'terms.s7.body':
			'En la medida permitida por la ley nicaragüense, Vanter no será responsable por daños indirectos, incidentales o lucro cesante derivados del uso o la imposibilidad de uso de Ember.',
		'terms.s8.title': '8. Ley aplicable y jurisdicción',
		'terms.s8.body':
			'Estos términos se rigen por las leyes de la República de Nicaragua. Cualquier controversia se someterá a los jueces competentes de Managua, Nicaragua.',
		'terms.s9.title': '9. Contacto',
		'terms.s9.body':
			'Consultas sobre estos términos: tofernandoband01@outlook.com — Vanter, Managua, Nicaragua.',

		// --- 404 ---
		'404.title': 'Página no encontrada — Ember',
		'404.desc': 'La página que buscas no existe o fue movida.',
		'404.h1': 'Esta página no existe',
		'404.body':
			'Puede que el enlace esté roto o la página se haya movido. Volvé al inicio para seguir explorando Ember.',
		'404.home': 'Volver al inicio',

		'lang.es': 'ES',
		'lang.en': 'EN'
	},

	en: {
		// --- Nav ---
		'nav.features': 'Features',
		'nav.pricing': 'Pricing',
		'nav.info': 'Info',
		'nav.contact': 'Contact',
		'nav.login': 'Log in',
		'nav.register': 'Sign up',
		'nav.toggleTheme': 'Toggle theme',
		'nav.openMenu': 'Open menu',
		'nav.closeMenu': 'Close menu',
		'nav.langLabel': 'Language',

		// --- Hero ---
		'hero.eyebrow': 'Real-time restaurant management',
		'hero.title.prefix': 'Your restaurant,',
		'hero.title.accent': 'in sync',
		'hero.lede':
			'Collaborative cart, kitchen tickets, floor management and analytics in one panel. No friction, no delays.',
		'hero.cta.register': 'Sign up',
		'hero.cta.plans': 'See plans',
		'hero.bar.1': 'QR collaborative cart',
		'hero.bar.2': 'Kitchen display (KDS)',
		'hero.bar.3': 'Bill splitting & cash close',
		'hero.bar.4': 'Analytics by period',
		'hero.bar.5': 'Multi-restaurant',
		'hero.card.title': 'Table 8 · ready',
		'hero.card.sub': 'Kitchen marked it 2s ago',
		'hero.shotAlt': 'The Ember dashboard showing the waiter’s real-time table view',

		// --- Compare ---
		'cmp.eyebrow': 'Ember vs. the usual',
		'cmp.title': 'The same service, without the bottlenecks',
		'cmp.col.voice': 'Voice or paper orders',
		'cmp.col.pos': 'Traditional POS',
		'cmp.col.ember': 'Ember',
		'cmp.r1.label': 'Taking the order',
		'cmp.r1.voice': 'The server writes it down and walks each order to the kitchen',
		'cmp.r1.pos': 'The server types everything into a fixed terminal',
		'cmp.r1.ember': 'Guests order from their phone; the kitchen sees it instantly',
		'cmp.r2.label': 'Splitting the bill',
		'cmp.r2.voice': 'By hand, slow and error-prone',
		'cmp.r2.pos': 'Limited feature, always done by the server',
		'cmp.r2.ember': 'Each guest confirms and pays their share from the table',
		'cmp.r3.label': 'Kitchen status',
		'cmp.r3.voice': '"Is table 8 ready yet?" shouted across the line',
		'cmp.r3.pos': 'Printed ticket, no tracking',
		'cmp.r3.ember': 'KDS screen with states and overdue alerts',
		'cmp.r4.label': 'Getting started',
		'cmp.r4.voice': 'Free, but it all lives in the staff’s head',
		'cmp.r4.pos': 'Install, proprietary hardware and training',
		'cmp.r4.ember': 'Create the account, load the menu, runs in the browser',

		// --- Trust ---
		'trust.eyebrow': 'No fine print',
		'trust.title': 'What you can expect from Ember',
		'trust.1': 'A real Free plan: no card, no time limit',
		'trust.2': 'Cancel anytime, no penalty',
		'trust.3': 'Each restaurant’s data stays isolated',
		'trust.4': 'Works with your network or Windows printers',
		'trust.5': 'Guests install nothing — they join from the browser',
		'trust.closing': 'Ember is built by Vanter, from Managua, Nicaragua.',
		'trust.contact': 'Talk to the team',

		// --- How it works ---
		'how.eyebrow': 'How it works',
		'how.title': 'Three steps, from QR to checkout',
		'how.1.title': 'The guest joins',
		'how.1.body': 'Scans the table QR or enters a 5-character code. Nothing to install.',
		'how.2.title': 'They order from the table',
		'how.2.body':
			'Everyone sees the same shared cart in real time, and each person confirms and pays their share.',
		'how.3.title': 'Kitchen and checkout, live',
		'how.3.body':
			'The order reaches the kitchen screen and the server panel instantly: prep, payment and cash close.',

		// --- Features teaser ---
		'ftease.eyebrow': 'Features',
		'ftease.title': 'The whole service in one panel',
		'ftease.link': 'See all features',

		// --- Why Ember ---
		'why.eyebrow': 'Why Ember',
		'why.title': 'Built for the pace of a restaurant',
		'why.1.title': 'Real time',
		'why.1.body':
			'Tables, orders and payments sync instantly over WebSocket across every device.',
		'why.2.title': 'No app for the guest',
		'why.2.body': 'They join via QR from the phone browser. Nothing to download or install.',
		'why.3.title': 'One panel',
		'why.3.body':
			'Cart, kitchen, floor, payments and analytics on the same platform — no fragile integrations.',
		'why.4.title': 'Multi-restaurant',
		'why.4.body':
			'Each location isolated and configurable; add branches without switching systems.',

		// --- FAQ ---
		'faq.eyebrow': 'FAQ',
		'faq.title': 'Before you start',
		'faq.1.q': 'Does the guest have to install an app?',
		'faq.1.a':
			'No. They join the table by scanning the QR or entering a 5-character code; everything runs in the phone browser.',
		'faq.2.q': 'Does it work for a single location?',
		'faq.2.a':
			'Yes. Ember works the same for one restaurant or a chain; each location is isolated and you can add branches whenever you want.',
		'faq.3.q': 'How do guests pay?',
		'faq.3.a':
			'Each participant confirms and pays their own share of the shared cart. The server can also charge the whole table, split or merge bills, and run the per-shift cash close.',
		'faq.4.q': 'Do I need a special printer?',
		'faq.4.a':
			'Ember’s print agent connects network or Windows-queue printers and sends tickets and receipts to them automatically. No proprietary hardware required.',
		'faq.5.q': 'Can I try it for free?',
		'faq.5.a':
			'Yes, the Free plan has no cost and no card. You can compare every plan on the pricing page.',
		'faq.6.q': 'How do I get started?',
		'faq.6.a':
			'You create the account and the setup wizard guides you through loading tables and the catalog. If you’d rather have a guided demo, get in touch.',
		'faq.link.plans': 'See plans',
		'faq.link.contact': 'Contact',

		// --- CTA ---
		'cta.eyebrow': 'No friction, no delays',
		'cta.title': 'Put your restaurant in real time',
		'cta.lede':
			'Join the restaurants already running tables, orders and analytics from one panel.',
		'cta.register': 'Sign up free',
		'cta.plans': 'or see the plans →',

		// --- Footer ---
		'footer.tagline':
			'Real-time restaurant management: orders, tables and analytics in one panel.',
		'footer.legal': 'Legal',
		'footer.privacy': 'Privacy policy',
		'footer.terms': 'Terms of service',
		'footer.contact': 'Contact',
		'footer.rights': 'All rights reserved.',

		// --- Cookie banner ---
		'cookie.text': 'We use essential cookies for the site to work. See our',
		'cookie.link': 'Privacy Policy',
		'cookie.accept': 'Accept',

		// --- Sticky mobile CTA ---
		'sticky.login': 'Log in',
		'sticky.register': 'Sign up',

		// --- Features (data) ---
		'feat.cart.title': 'Collaborative cart',
		'feat.cart.desc':
			'Guests join with a QR code and add dishes to a shared cart in real time — no apps to install.',
		'feat.cart.p1': 'Join by QR or 5-character code',
		'feat.cart.p2': 'Live shared cart across all guests',
		'feat.cart.p3': 'Each participant confirms and pays their own share',
		'feat.cart.p4': 'No install: it runs in the phone browser',
		'feat.kds.title': 'Kitchen display (KDS)',
		'feat.kds.desc':
			'Every order moves through controlled states — pending, preparing, ready, delivered — visible on screen instantly.',
		'feat.kds.p1': 'Real-time order queue per station',
		'feat.kds.p2': 'Pending → Preparing → Ready → Delivered transitions',
		'feat.kds.p3': 'Instant sync over WebSocket',
		'feat.kds.p4': 'Overdue orders flag themselves',
		'feat.floor.title': 'Floor & server management',
		'feat.floor.desc':
			'Table assignment, manual add-ons, bill splitting and per-shift cash close, all from one panel.',
		'feat.floor.p1': 'Every table in the room on a live map',
		'feat.floor.p2': 'Open, assign and close table sessions',
		'feat.floor.p3': 'Manual item add-ons and bill split or merge',
		'feat.floor.p4': 'Per-shift cash close with reconciliation',
		'feat.analytics.title': 'Analytics for admins',
		'feat.analytics.desc':
			'Sales metrics, average check and performance by product or table, filterable by day, week, month or year.',
		'feat.analytics.p1': 'Sales, average check and top-selling items',
		'feat.analytics.p2': 'Day, week, month and year filters',
		'feat.analytics.p3': 'Performance by product and by table',
		'feat.analytics.p4': 'Staff, role and restaurant configuration management',
		'feat.shotAlt': 'Ember view:',

		// --- Plans (data) ---
		'plan.free.tagline': 'To try Ember with no commitment.',
		'plan.free.f1': '1 active table',
		'plan.free.f2': 'Collaborative cart',
		'plan.free.f3': 'Kitchen display (KDS)',
		'plan.free.f4': 'Community support',
		'plan.free.cta': 'Start free',
		'plan.starter.tagline': 'For small restaurants and cafés.',
		'plan.starter.f1': 'Up to 10 tables',
		'plan.starter.f2': 'KDS and floor management',
		'plan.starter.f3': 'Bill splitting',
		'plan.starter.f4': 'Email support',
		'plan.starter.cta': 'Sign up',
		'plan.pro.tagline': 'For higher-volume restaurants.',
		'plan.pro.f1': 'Unlimited tables',
		'plan.pro.f2': 'Advanced analytics',
		'plan.pro.f3': 'Multiple servers and roles',
		'plan.pro.f4': 'Priority support',
		'plan.pro.cta': 'Sign up',
		'plan.ent.tagline': 'For chains and franchises.',
		'plan.ent.f1': 'Multi-branch',
		'plan.ent.f2': 'Dedicated SLA',
		'plan.ent.f3': 'Custom integrations',
		'plan.ent.f4': 'Account manager',
		'plan.ent.cta': 'Talk to sales',
		'plan.priceCustom': 'Custom',
		'plan.perMonth': '/mo',
		'plan.perYear': '/yr',
		'plan.mostPopular': 'Most popular',

		// --- Pricing table ---
		'ptable.heading': 'Plan comparison',
		'ptable.g.floor': 'Floor operations',
		'ptable.g.analytics': 'Analytics',
		'ptable.g.team': 'Team & access',
		'ptable.g.scale': 'Scale & support',
		'ptable.r.tables': 'Active tables',
		'ptable.r.cart': 'Collaborative cart (QR / code)',
		'ptable.r.kds': 'Kitchen display (KDS)',
		'ptable.r.floor': 'Floor & server management',
		'ptable.r.split': 'Bill split and merge',
		'ptable.r.cashclose': 'Per-shift cash close',
		'ptable.r.print': 'Ticket and receipt printing',
		'ptable.r.rooms': 'Multiple rooms / areas',
		'ptable.r.metrics': 'Basic sales metrics',
		'ptable.r.periodfilters': 'Day / week / month / year filters',
		'ptable.r.advanced': 'Advanced analytics (avg check, product, table)',
		'ptable.r.export': 'Report export',
		'ptable.r.roles': 'Server / Kitchen / Admin roles',
		'ptable.r.staff': 'Staff management',
		'ptable.r.multiwaiter': 'Multiple concurrent servers',
		'ptable.r.branding': 'Brand configuration (branding)',
		'ptable.r.multibranch': 'Multi-branch',
		'ptable.r.integrations': 'Custom integrations',
		'ptable.r.sla': 'Dedicated SLA',
		'ptable.r.am': 'Account manager',
		'ptable.r.support': 'Support',
		'ptable.v.unlimited': 'Unlimited',
		'ptable.v.community': 'Community',
		'ptable.v.email': 'Email',
		'ptable.v.priority': 'Priority',
		'ptable.v.dedicated': 'Dedicated 24/7',
		'ptable.included': 'Included',
		'ptable.notIncluded': 'Not included',

		// --- Page: index (SEO) ---
		'seo.home.title': 'Ember — Restaurant Management Platform',
		'seo.home.desc':
			'Collaborative real-time ordering, kitchen display, floor management and analytics for modern restaurants.',

		// --- Page: funcionalidades ---
		'fpage.title': 'Features — Ember',
		'fpage.desc':
			'Collaborative cart, kitchen display (KDS), floor management and analytics — everything Ember does.',
		'fpage.eyebrow': 'Features',
		'fpage.h1': 'Built for the real flow of a restaurant',
		'fpage.lede':
			'Four modules working on the same real-time panel, from the guest scanning the QR to the cash close.',

		// --- Page: planes ---
		'ppage.title': 'Plans & pricing — Ember',
		'ppage.desc':
			'Compare Ember’s Free, Starter, Pro and Enterprise plans: tables, KDS, analytics, roles and support.',
		'ppage.eyebrow': 'Plans & pricing',
		'ppage.h1': 'Pick the plan that fits your operation',
		'ppage.lede':
			'Every plan includes the collaborative cart and kitchen tickets. Start free, no card, and change plan whenever you want, no system migration.',
		'ppage.section2': 'What each plan includes',
		'ppage.section2sub': 'Full feature breakdown by plan.',
		'ppage.fineprint':
			'Prices in USD, per restaurant, taxes not included. On the annual plan you pay for 10 months and use 12. Enterprise is quoted by number of branches and volume.',
		'ppage.billing.label': 'Billing cycle',
		'ppage.billing.monthly': 'Monthly',
		'ppage.billing.annual': 'Annual',
		'ppage.billing.save': '2 months free',

		// --- Page: contacto ---
		'cpage.title': 'Contact — Ember',
		'cpage.desc': 'How to reach the Ember team: email, office and support hours.',
		'cpage.eyebrow': 'Let’s talk',
		'cpage.h1': 'We’re here to help you put your restaurant on Ember',
		'cpage.lede':
			'Write to us and we’ll set up a demo or build a plan tailored to your operation.',
		'cpage.mail.title': 'Email',
		'cpage.mail.body': 'For demos, custom plans and support. We reply within one business day.',
		'cpage.office.title': 'Office',
		'cpage.hours.title': 'Hours',
		'cpage.hours.body': 'Monday to Friday<br />8:00 to 18:00 (GMT−6)',
		'cpage.demo.text':
			'Prefer to see it in action? We’ll set up a 20-minute video demo.',
		'cpage.demo.cta': 'Request a demo',

		// --- Info section ---
		'info.sidebar': 'Information',
		'info.nav.overview': 'Overview',
		'info.nav.manual': 'User manual',
		'info.nav.videos': 'Ember videos',
		'info.title': 'Information — Ember',
		'info.desc': 'Ember user manual and videos: learn how to use the platform.',
		'info.h1': 'Learn to use Ember',
		'info.lede':
			'Documentation and video material to get your restaurant running and get the most out of the platform.',
		'info.card.manual.title': 'User manual',
		'info.card.manual.body':
			'How to use Ember step by step: getting started, roles, floor and tables, kitchen, payments and analytics.',
		'info.card.videos.title': 'Ember videos',
		'info.card.videos.body': 'Video walkthroughs of the platform running in a real service.',

		'manual.title': 'User manual — Ember',
		'manual.desc': 'Ember usage guide: getting started, roles, floor, kitchen, payments and analytics.',
		'manual.h1': 'User manual',
		'manual.lede':
			'How each Ember module works: what it does, who uses it and the typical flow of a service. Use the index to jump to the module you need.',
		'manual.toc': 'On this page',
		'manual.s1.title': 'Getting started',
		'manual.s1.body':
			'When you create your restaurant account, a setup wizard walks you through loading the business details, the tables in the room and the initial catalog: categories, priced dishes and, if you use them, modifier groups (for example “steak doneness” or “extras”). When you finish you land in the admin panel, where everything is configured and monitored. Floor and kitchen staff use the same web address to sign in with the credentials the admin creates for them.',
		'manual.s2.title': 'Roles & access',
		'manual.s2.body':
			'Ember has four roles. The Admin configures the catalog and the operation, manages staff and sees the analytics. The Server works the room: opens and closes tables, adds items, takes payment and runs the cash close. Kitchen sees only the ticket screen. The Guest has no account: they join the table by QR or code and only see their own cart. The admin creates each person and assigns their role; everyone sees only what their role allows.',
		'manual.s3.title': 'Floor & tables',
		'manual.s3.body':
			'The floor view is a map of every table in the room with its live status: free or occupied, and how many guests are seated. From there the server opens a table session, assigns it, merges or splits tables, and frees it when the bill closes. Everything syncs instantly across devices, so two servers never see different states for the same table.',
		'manual.s4.title': 'Collaborative cart',
		'manual.s4.body':
			'The guest joins their table session by scanning the QR or entering a 5-character code in the phone browser, with no app to install. Everyone at the table shares the same cart and watches it update in real time as each person adds dishes. Each participant confirms and pays their own share, or the server charges the whole table. On confirmation, the order goes straight to the kitchen.',
		'manual.s5.title': 'Kitchen display (KDS)',
		'manual.s5.body':
			'Every confirmed order appears on the kitchen screen and moves through controlled states: Pending → Preparing → Ready → Delivered. The kitchen taps each card to move its state and the change reflects instantly on the server panel. Orders that sit too long without progress flag themselves. You can run one screen per station — say kitchen and bar — each showing only what belongs to it.',
		'manual.s6.title': 'Bills & payments',
		'manual.s6.body':
			'From a table panel the server sees everything ordered, adds manual items that did not go through the cart, and splits or merges bills depending on how the guests want to pay. Each payment is recorded with its method. At the end of the shift the cash close with reconciliation is done: Ember compares what should be in the drawer against what the server counts. The shift has a closing time; if it passes, Ember warns and asks to close or extend it before taking more cash.',
		'manual.s7.title': 'Analytics',
		'manual.s7.body':
			'The admin analytics panel shows total sales, average check and top-selling products, with day, week, month and year filters. It also breaks down performance by product, by table and by server, so you can see what sells, which table turns most and how each team member is doing. The data updates as payments come in.',
		'manual.s8.title': 'Ticket & receipt printing',
		'manual.s8.body':
			'Ember prints kitchen tickets and customer receipts through a print agent running on a computer in the venue. The agent connects network or Windows-queue printers and receives jobs automatically when an order is confirmed or a table is charged. It supports thermal (ESC/POS) printers and also regular driver printers, for venues without a dedicated thermal one. It is set up once, assigning each printer to a role (kitchen, bar, register).',
		'manual.s9.title': 'Restaurant configuration',
		'manual.s9.body':
			'In Settings the admin adjusts the branding guests see (logo and colors), the ticket format, the full catalog (categories, dishes, prices and modifier groups), tables and rooms, staff and their roles, printers and the business details. Every settings screen has a help button that opens a guided tour of that section.',
		'manual.s10.title': 'Troubleshooting',
		'manual.s10.body':
			'The guest cannot join by QR: check that the table has an open session and the phone has internet; you can always read them the table’s 5-character code.\nThe printer does not print: make sure the computer running the print agent is on and connected, the printer has paper, and it is assigned to the right role in Settings.\nAn order does not show in the kitchen: only confirmed orders are sent; anything left in the cart unconfirmed does not go through. Also check the screen is set to the right station.\n“Cash shift overdue”: the shift has passed its closing time. Run the cash close with reconciliation, or extend the shift if service is still going.\nYou cannot see a module or an action: it is almost always because your role has no access to that part. Ask the admin to check your role.',

		'videos.title': 'Ember videos — Ember',
		'videos.desc': 'Ember in action: platform walkthroughs in a real service.',
		'videos.h1': 'Ember videos',
		'videos.lede':
			'Short walkthroughs of Ember running in a real service. We publish them as they are ready; in the meantime, the manual covers every module in writing.',
		'videos.soon': 'Coming soon',
		'videos.play': 'Play',
		'videos.close': 'Close',
		'videos.1.title': 'General walkthrough',
		'videos.1.body': 'At a glance: floor, kitchen, payments and analytics, and how the modules fit together.',
		'videos.2.title': 'Restaurant setup',
		'videos.2.body':
			'The setup wizard step by step: business details, tables and loading the catalog with prices and modifiers.',
		'videos.3.title': 'Full table service',
		'videos.3.body':
			'A service from start to finish: the guest joins by QR, orders, the kitchen prepares and the server closes the bill.',
		'videos.4.title': 'Live kitchen (KDS)',
		'videos.4.body':
			'The ticket screen in action: orders coming in, state changes and how overdue ones are flagged.',
		'videos.5.title': 'Cash close',
		'videos.5.body':
			'The shift close with reconciliation: what Ember shows, how it is balanced and what happens if the shift goes overdue.',
		'videos.6.title': 'Analytics for admins',
		'videos.6.body':
			'The admin panel: sales, average check and performance by product, table and server, with period filters.',

		// --- Legal: privacy ---
		'legal.eyebrow': 'Legal',
		'legal.updated': 'Last updated:',
		'privacy.title': 'Privacy policy — Ember',
		'privacy.desc':
			'How Vanter collects, uses and protects the personal data of Ember users.',
		'privacy.h1': 'Privacy policy',
		'privacy.date': 'August 29, 2026',
		'privacy.s1.title': '1. Data controller',
		'privacy.s1.body':
			'Vanter, with address in Managua, Nicaragua, is the controller of the personal data collected through Ember and this site.',
		'privacy.s2.title': '2. Data we collect',
		'privacy.s2.body':
			'We collect the data a restaurant and its staff enter when using Ember (name, email, role, business billing details) and the data a guest enters when joining a table session (display name, orders). We also record basic technical browsing data on this site (pages visited, device type) to improve the product.',
		'privacy.s3.title': '3. Use of data',
		'privacy.s3.body':
			'We use data only to provide the service: authenticate users, process orders and payments, generate analytics for the restaurant that manages the account, and answer sales or support inquiries. We do not sell personal data to third parties.',
		'privacy.s4.title': '4. Cookies',
		'privacy.s4.body':
			'This site uses only strictly necessary cookies to function. For traffic measurement we use a cookieless, non-advertising analytics tool that does not track across sites or identify individuals. You can manage any cookie from your browser at any time.',
		'privacy.s5.title': '5. Retention',
		'privacy.s5.body':
			'We keep data while the restaurant account remains active and for the additional period required by legal or contractual obligations. A guest who takes part in a table session does not need prior registration; their session data is kept according to the retention policies of the corresponding restaurant.',
		'privacy.s6.title': '6. Data subject rights',
		'privacy.s6.body':
			'You can request access, rectification or deletion of your personal data by writing to tofernandoband01@outlook.com. We will respond within the terms set by Law No. 787, the Personal Data Protection Law of Nicaragua.',
		'privacy.s7.title': '7. Contact',
		'privacy.s7.body':
			'For any question about this policy, write to tofernandoband01@outlook.com or contact Vanter, Managua, Nicaragua.',

		// --- Legal: terms ---
		'terms.title': 'Terms of service — Ember',
		'terms.desc': 'Terms of use for the Ember platform, operated by Vanter.',
		'terms.h1': 'Terms of service',
		'terms.date': 'August 29, 2026',
		'terms.s1.title': '1. Acceptance of terms',
		'terms.s1.body':
			'By creating an account or using Ember, the contracting restaurant and its staff accept these Terms of Service and the Privacy Policy. If you do not agree, you must not use the service.',
		'terms.s2.title': '2. Description of the service',
		'terms.s2.body':
			'Ember is a restaurant management platform provided by Vanter that includes a collaborative guest cart, kitchen display (KDS), floor/server management, billing and admin analytics, according to the contracted plan.',
		'terms.s3.title': '3. Accounts & responsibility',
		'terms.s3.body':
			'The restaurant is responsible for keeping its staff credentials confidential and for all activity carried out under its account. Vanter is not liable for unauthorized access arising from negligent handling of credentials.',
		'terms.s4.title': '4. Plans & billing',
		'terms.s4.body':
			'Access to Ember is governed by the contracted plan (FREE, STARTER, PRO or ENTERPRISE). Plan changes, suspension or cancellation are handled as commercially agreed with Vanter or from the corresponding admin panel.',
		'terms.s5.title': '5. Acceptable use',
		'terms.s5.body':
			'It is not permitted to use Ember for unlawful purposes, to interfere with the operation of the service, to attempt to access another restaurant’s (tenant’s) data without authorization, or to reverse-engineer the platform.',
		'terms.s6.title': '6. Service availability',
		'terms.s6.body':
			'Vanter makes reasonable efforts to keep Ember available but does not guarantee an interruption-free service. Scheduled or emergency maintenance may be carried out with or without prior notice depending on criticality.',
		'terms.s7.title': '7. Limitation of liability',
		'terms.s7.body':
			'To the extent permitted by Nicaraguan law, Vanter will not be liable for indirect or incidental damages or lost profits arising from the use of, or inability to use, Ember.',
		'terms.s8.title': '8. Governing law & jurisdiction',
		'terms.s8.body':
			'These terms are governed by the laws of the Republic of Nicaragua. Any dispute will be submitted to the competent courts of Managua, Nicaragua.',
		'terms.s9.title': '9. Contact',
		'terms.s9.body':
			'Questions about these terms: tofernandoband01@outlook.com — Vanter, Managua, Nicaragua.',

		// --- 404 ---
		'404.title': 'Page not found — Ember',
		'404.desc': 'The page you are looking for does not exist or was moved.',
		'404.h1': 'This page does not exist',
		'404.body':
			'The link may be broken or the page may have moved. Head back home to keep exploring Ember.',
		'404.home': 'Back to home',

		'lang.es': 'ES',
		'lang.en': 'EN'
	}
};
