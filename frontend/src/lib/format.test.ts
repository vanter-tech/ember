import { formatCurrency } from './format'

test('formats a whole number with two decimals and a dollar sign', () => {
  expect(formatCurrency(100)).toBe('$100.00')
})

test('formats a decimal value rounded to two places', () => {
  expect(formatCurrency(265.5)).toBe('$265.50')
})

test('inserts a thousands separator', () => {
  expect(formatCurrency(1234.5)).toBe('$1,234.50')
})

test('formats a negative value (variance can go negative)', () => {
  expect(formatCurrency(-5)).toBe('-$5.00')
})
