import { describe, expect, test } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AnimatedTabContent } from './AnimatedTabContent'

describe('AnimatedTabContent', () => {
  test('wraps the tab in the same fade/slide-in used for page transitions', () => {
    render(
      <AnimatedTabContent tabKey="BRANDING">
        <p>branding</p>
      </AnimatedTabContent>,
    )

    const wrapper = screen.getByText('branding').parentElement!
    expect(wrapper).toHaveClass('animate-in', 'fade-in', 'slide-in-from-bottom-2')
    expect(wrapper).toHaveClass('motion-reduce:animate-none')
  })

  test('switching tab remounts the wrapper so the animation plays again', () => {
    const { rerender } = render(
      <AnimatedTabContent tabKey="BRANDING">
        <p>branding</p>
      </AnimatedTabContent>,
    )
    const first = screen.getByText('branding').parentElement

    rerender(
      <AnimatedTabContent tabKey="MENU">
        <p>menu</p>
      </AnimatedTabContent>,
    )

    expect(screen.getByText('menu').parentElement).not.toBe(first)
    expect(first?.isConnected).toBe(false)
  })

  test('re-rendering the SAME tab keeps the wrapper (no replay while editing a form)', () => {
    const { rerender } = render(
      <AnimatedTabContent tabKey="BRANDING">
        <p>one</p>
      </AnimatedTabContent>,
    )
    const first = screen.getByText('one').parentElement

    rerender(
      <AnimatedTabContent tabKey="BRANDING">
        <p>two</p>
      </AnimatedTabContent>,
    )

    expect(screen.getByText('two').parentElement).toBe(first)
  })
})
