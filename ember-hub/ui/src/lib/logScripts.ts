export const START_SCRIPTS = {
  postgres: [
    'LOG:  starting PostgreSQL 16.6 on x86_64-pc-windows',
    'LOG:  listening on IPv4 address "127.0.0.1", port 5432',
    'LOG:  database system is ready to accept connections'
  ],
  minio: [
    'API: http://127.0.0.1:9000',
    'Console: http://127.0.0.1:9001',
    'Status: 1 Online, 0 Offline.'
  ],
  server: [
    'Iniciando el servidor…',
    'Servicio web escuchando en el puerto 8080',
    'Servidor listo.'
  ]
} as const;

export const STOP_SCRIPTS = {
  postgres: [
    'LOG:  received fast shutdown request',
    'LOG:  database system is shut down'
  ],
  minio: [
    'Shutting down MinIO…',
    'MinIO instance stopped.'
  ],
  server: [
    'Cerrando el servidor…',
    'Servidor detenido.'
  ]
} as const;

export type ServiceId = keyof typeof START_SCRIPTS;
