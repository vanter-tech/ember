import type { StaffMember } from './types'

// Placeholder roster — replace with a `staffService` + `useQuery` call once
// a staff-listing endpoint exists on the backend.
export const MOCK_STAFF: StaffMember[] = [
  {
    id: 'stf-1',
    name: 'Sofía Ramírez',
    department: 'KITCHEN',
    roleLabel: 'Chef ejecutiva',
    status: 'ACTIVE',
    metadata: [
      { label: 'Turno', value: 'Mañana' },
      { label: 'Contrato', value: 'Tiempo completo' },
    ],
    pendingHours: 0,
  },
  {
    id: 'stf-2',
    name: 'Diego Fernández',
    department: 'KITCHEN',
    roleLabel: 'Cocinero de línea',
    status: 'OFFLINE',
    metadata: [
      { label: 'Turno', value: 'Noche' },
      { label: 'Contrato', value: 'Medio tiempo' },
    ],
    pendingHours: 3.5,
  },
  {
    id: 'stf-3',
    name: 'Valentina Torres',
    department: 'DINING_ROOM',
    roleLabel: 'Mesera senior',
    status: 'ACTIVE',
    metadata: [
      { label: 'Turno', value: 'Tarde' },
      { label: 'Eficiencia', value: '94%' },
    ],
    pendingHours: 0,
  },
  {
    id: 'stf-4',
    name: 'Mateo Rojas',
    department: 'DINING_ROOM',
    roleLabel: 'Mesero',
    status: 'ACTIVE',
    metadata: [
      { label: 'Turno', value: 'Mañana' },
      { label: 'Eficiencia', value: '88%' },
    ],
    pendingHours: 1.5,
  },
  {
    id: 'stf-5',
    name: 'Camila Herrera',
    department: 'ADMINISTRATION',
    roleLabel: 'Gerente de turno',
    status: 'ACTIVE',
    metadata: [
      { label: 'Ubicación', value: 'Piso principal' },
      { label: 'Contrato', value: 'Tiempo completo' },
    ],
    pendingHours: 0,
  },
  {
    id: 'stf-6',
    name: 'Andrés Molina',
    department: 'CLEANING',
    roleLabel: 'Supervisor de limpieza',
    status: 'OFFLINE',
    metadata: [
      { label: 'Turno', value: 'Noche' },
      { label: 'Contrato', value: 'Tiempo completo' },
    ],
    pendingHours: 2,
  },
  {
    id: 'stf-7',
    name: 'Isabella Castro',
    department: 'ADMINISTRATION',
    roleLabel: 'Asistente administrativa',
    status: 'ACTIVE',
    metadata: [
      { label: 'Turno', value: 'Mañana' },
      { label: 'Eficiencia', value: '91%' },
    ],
    pendingHours: 0.5,
  },
]
