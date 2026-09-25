import { describe, expect, test } from 'vitest'
import { businessInfoLines, hoursLines } from '@/lib/receiptBusinessInfo'

const day = (d: string, open: string, close: string, closed = false) =>
  ({ day: d, openTime: open, closeTime: close, closed }) as never

describe('hoursLines', () => {
  test('the same hours every day is one line', () => {
    const schedule = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map((d) =>
      day(d, '12:00', '23:00')
    )
    expect(hoursLines({ businessHours: { schedule } } as never)).toEqual(['Horario: Lun-Dom 12:00-23:00'])
  })

  test('collapses consecutive equal days and names the closed ones', () => {
    const schedule = [
      day('MONDAY', '12:00', '22:00'),
      day('TUESDAY', '12:00', '22:00'),
      day('WEDNESDAY', '12:00', '22:00'),
      day('THURSDAY', '12:00', '22:00'),
      day('FRIDAY', '12:00', '22:00'),
      day('SATURDAY', '12:00', '23:30'),
      day('SUNDAY', '', '', true),
    ]
    expect(hoursLines({ businessHours: { schedule } } as never)).toEqual([
      'Horario:',
      'Lun-Vie 12:00-22:00',
      'Sab 12:00-23:30',
      'Dom cerrado',
    ])
  })

  test('falls back to the Branding range, or nothing', () => {
    expect(hoursLines({ branding: { openingTime: '12:00', closingTime: '23:00' } } as never)).toEqual([
      'Horario: 12:00 - 23:00',
    ])
    expect(hoursLines({} as never)).toEqual([])
  })
})

describe('businessInfoLines', () => {
  const settings = {
    branding: { ruc: 'J031', address: 'Calle 1', phone: '2222', openingTime: '12:00', closingTime: '23:00' },
  } as never

  test('RUC, address and phone only when switched on, before the hours', () => {
    expect(businessInfoLines(settings, { showHours: true, showInfo: false })).toEqual(['Horario: 12:00 - 23:00'])
    expect(businessInfoLines(settings, { showHours: true, showInfo: true })).toEqual([
      'RUC: J031',
      'Calle 1',
      'Tel: 2222',
      'Horario: 12:00 - 23:00',
    ])
  })

  test('hours can be switched off', () => {
    expect(businessInfoLines(settings, { showHours: false, showInfo: false })).toEqual([])
  })
})
