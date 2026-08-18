import { render, screen } from '@testing-library/react'
import { LanguageSwitcher } from '@/components/LanguageSwitcher'
import { useLocaleStore } from '@/store/localeStore'

beforeEach(() => {
  useLocaleStore.setState({ locale: 'es' })
})

test('renders the current locale abbreviation', () => {
  render(<LanguageSwitcher />)
  expect(screen.getByTestId('language-switcher-trigger')).toHaveTextContent('ES')
})

test('setLocale updates the store', () => {
  useLocaleStore.getState().setLocale('en')
  expect(useLocaleStore.getState().locale).toBe('en')
})
