import { describe, expect, test } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from './dialog'

// The overlays animate through `data-open:` / `data-closed:` classes (written for base-ui). Radix only
// reports `data-state`, so without the `data-open` / `data-closed` variants in index.css the modals just popped in
// (jsdom cannot evaluate that CSS; it was checked against the built stylesheet and in a browser).
describe('overlay enter/exit animation', () => {
  test('an open Radix dialog reports data-state="open" and carries the enter animation classes', () => {
    render(
      <Dialog open>
        <DialogContent>
          <DialogTitle>Título</DialogTitle>
          <DialogDescription>Descripción</DialogDescription>
        </DialogContent>
      </Dialog>,
    )

    const content = screen.getByRole('dialog')
    expect(content).toHaveAttribute('data-state', 'open')
    expect(content.className).toContain('data-open:animate-in')
    expect(content.className).toContain('data-closed:animate-out')
  })
})
