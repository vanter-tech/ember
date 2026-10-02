import { describe, expect, test } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Skeleton } from '@/components/ui/skeleton'
import { LoadingStatus } from './LoadingStatus'
import { SettingsFormSkeleton } from './SettingsFormSkeleton'
import { CardGridSkeleton } from './CardGridSkeleton'
import { ListRowsSkeleton } from './ListRowsSkeleton'
import { ChartSkeleton } from './ChartSkeleton'
import { BarRowsSkeleton } from './BarRowsSkeleton'
import { PageHeaderSkeleton } from './PageHeaderSkeleton'

describe('Skeleton', () => {
  test('is a decorative block that only pulses when motion is allowed', () => {
    render(<Skeleton data-testid="s" className="h-4 w-10" />)
    const block = screen.getByTestId('s')

    expect(block).toHaveAttribute('aria-hidden', 'true')
    expect(block).toHaveAttribute('data-slot', 'skeleton')
    expect(block).toHaveClass('h-4', 'w-10', 'motion-safe:animate-pulse')
  })
})

describe('LoadingStatus', () => {
  test('announces the loading state to assistive tech without showing text', () => {
    render(<LoadingStatus label="Cargando datos..." />)

    const status = screen.getByRole('status')
    expect(status).toHaveTextContent('Cargando datos...')
    expect(status).toHaveClass('sr-only')
  })
})

describe('SettingsFormSkeleton', () => {
  test('draws a card with the requested number of field rows and announces loading', () => {
    render(<SettingsFormSkeleton label="Cargando configuraciones..." fields={4} />)

    expect(screen.getAllByTestId('skeleton-field')).toHaveLength(4)
    expect(screen.getByText('Cargando configuraciones...')).toBeInTheDocument()
  })

  test('draws the rows of the given layout, with the real card frame', () => {
    render(<SettingsFormSkeleton label="x" layout={['pair', 'toggle', 'chips', 'preview']} />)

    expect(screen.getAllByTestId('skeleton-field')).toHaveLength(2)
    expect(screen.getAllByTestId('skeleton-toggle')).toHaveLength(1)
    expect(screen.getAllByTestId('skeleton-chips')).toHaveLength(1)
    expect(screen.getAllByTestId('skeleton-preview')).toHaveLength(1)
    expect(screen.getByTestId('skeleton-settings-header')).toBeInTheDocument()
    expect(screen.getByTestId('skeleton-settings-footer')).toBeInTheDocument()
  })

  test('defaults to five fields', () => {
    render(<SettingsFormSkeleton label="x" />)
    expect(screen.getAllByTestId('skeleton-field')).toHaveLength(5)
  })
})

describe('CardGridSkeleton', () => {
  test('renders the requested number of placeholder cards with the given classes', () => {
    render(<CardGridSkeleton label="Cargando..." count={4} className="grid-cols-2" itemClassName="h-40" />)

    const cards = screen.getAllByTestId('skeleton-card')
    expect(cards).toHaveLength(4)
    expect(cards[0]).toHaveClass('h-40')
    expect(cards[0].parentElement).toHaveClass('grid', 'grid-cols-2')
    expect(screen.getByText('Cargando...')).toBeInTheDocument()
  })
})

describe('ListRowsSkeleton', () => {
  test('renders the requested number of rows', () => {
    render(<ListRowsSkeleton label="Cargando..." rows={3} />)

    expect(screen.getAllByTestId('skeleton-row')).toHaveLength(3)
    expect(screen.getByText('Cargando...')).toBeInTheDocument()
  })
})

describe('ChartSkeleton', () => {
  test('is one block with the chart height and announces the loading state', () => {
    render(<ChartSkeleton label="Cargando ventas..." />)

    expect(screen.getByTestId('skeleton-chart')).toHaveClass('h-64')
    expect(screen.getByText('Cargando ventas...')).toBeInTheDocument()
  })
})

describe('BarRowsSkeleton', () => {
  test('renders label + bar rows', () => {
    render(<BarRowsSkeleton label="Cargando..." rows={4} />)

    expect(screen.getAllByTestId('skeleton-bar-row')).toHaveLength(4)
    expect(screen.getByText('Cargando...')).toBeInTheDocument()
  })
})

describe('PageHeaderSkeleton', () => {
  test('is a title block and a subtitle block, with an optional round icon block', () => {
    const { rerender } = render(<PageHeaderSkeleton />)
    expect(screen.getByTestId('skeleton-page-header')).toBeInTheDocument()
    expect(screen.queryByTestId('skeleton-page-header-icon')).not.toBeInTheDocument()

    rerender(<PageHeaderSkeleton withIcon />)
    expect(screen.getByTestId('skeleton-page-header-icon')).toBeInTheDocument()
  })
})
