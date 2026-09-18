export type DenominationKind = 'BILL' | 'COIN'

export interface Denomination {
  id: string
  value: number
  kind: DenominationKind
}

export interface DenominationCount {
  denominationId: string
  quantity: number
}

/**
 * The 14 córdoba denominations in circulation per the Banco Central de Nicaragua
 * (bcn.gob.ni/billetes-actuales, bcn.gob.ni/monedas-actuales). Mirrors the backend's
 * NicaraguaDenominations.ALL exactly — kept in sync by hand, same as every other shared
 * enum/constant in this codebase (no shared package between frontend and backend).
 * bill_10 and coin_10 are deliberately both C$10 — the BCN has both a banknote and a coin at
 * that face value in circulation at once.
 */
export const NICARAGUA_DENOMINATIONS: Denomination[] = [
  { id: 'bill_1000', value: 1000, kind: 'BILL' },
  { id: 'bill_500', value: 500, kind: 'BILL' },
  { id: 'bill_200', value: 200, kind: 'BILL' },
  { id: 'bill_100', value: 100, kind: 'BILL' },
  { id: 'bill_50', value: 50, kind: 'BILL' },
  { id: 'bill_20', value: 20, kind: 'BILL' },
  { id: 'bill_10', value: 10, kind: 'BILL' },
  { id: 'coin_10', value: 10, kind: 'COIN' },
  { id: 'coin_5', value: 5, kind: 'COIN' },
  { id: 'coin_1', value: 1, kind: 'COIN' },
  { id: 'coin_050', value: 0.5, kind: 'COIN' },
  { id: 'coin_025', value: 0.25, kind: 'COIN' },
  { id: 'coin_010', value: 0.1, kind: 'COIN' },
  { id: 'coin_005', value: 0.05, kind: 'COIN' },
]

export const sumBreakdown = (breakdown: DenominationCount[]): number =>
  breakdown.reduce((total, entry) => {
    const denomination = NICARAGUA_DENOMINATIONS.find((d) => d.id === entry.denominationId)
    return denomination ? total + denomination.value * entry.quantity : total
  }, 0)
