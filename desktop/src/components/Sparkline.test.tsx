import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render } from '@testing-library/react'
import { Sparkline } from './Sparkline'

describe('Sparkline', () => {
  afterEach(() => cleanup())

  it('renders a polyline for values', () => {
    const { container } = render(<Sparkline values={[1, 2, 3, 2, 4]} />)
    const poly = container.querySelector('polyline')
    expect(poly).not.toBeNull()
    expect((poly!.getAttribute('points') ?? '').length).toBeGreaterThan(0)
  })

  it('renders an empty svg for no values', () => {
    const { container } = render(<Sparkline values={[]} />)
    expect(container.querySelector('polyline')).toBeNull()
    expect(container.querySelector('svg')).not.toBeNull()
  })
})
