import { describe, expect, it } from 'vitest'
import { DEFAULT_SOURCE, isDirty } from './ScriptEditor'
import type { ScriptDto } from '../types'

const script: ScriptDto = {
  id: 's1',
  name: 'Saved',
  source: 'public class A {}',
  sourceHash: 'h',
  createdAt: '',
  updatedAt: '',
}

describe('isDirty', () => {
  it('new script: clean when name empty and source is the default', () => {
    expect(isDirty('', DEFAULT_SOURCE, null)).toBe(false)
  })

  it('new script: dirty once name or source changes', () => {
    expect(isDirty('X', DEFAULT_SOURCE, null)).toBe(true)
    expect(isDirty('', DEFAULT_SOURCE + '\n', null)).toBe(true)
  })

  it('existing script: clean when name and source match the saved values', () => {
    expect(isDirty(script.name, script.source, script)).toBe(false)
  })

  it('existing script: dirty when the buffer diverges', () => {
    expect(isDirty('Saved2', script.source, script)).toBe(true)
    expect(isDirty(script.name, 'public class B {}', script)).toBe(true)
  })
})
