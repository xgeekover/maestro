import { describe, expect, it } from 'vitest'
import { registerJavaAssist } from './javaAssist'

interface Captured {
  completion?: { provideCompletionItems: (model: unknown, position: unknown) => { suggestions: Array<{ label: string }> } }
  hover?: { provideHover: (model: unknown, position: unknown) => unknown }
}

function mockMonaco(captured: Captured) {
  return {
    languages: {
      CompletionItemKind: { Snippet: 27, Method: 0 },
      CompletionItemInsertTextRule: { InsertAsSnippet: 4 },
      registerCompletionItemProvider: (_lang: string, p: Captured['completion']) => {
        captured.completion = p
      },
      registerHoverProvider: (_lang: string, p: Captured['hover']) => {
        captured.hover = p
      },
    },
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  } as any
}

describe('javaAssist', () => {
  it('registers completion + hover providers', () => {
    const captured: Captured = {}
    registerJavaAssist(mockMonaco(captured))
    expect(captured.completion).toBeTruthy()
    expect(captured.hover).toBeTruthy()
  })

  it('suggests SDK lifecycle + ctx members', () => {
    const captured: Captured = {}
    registerJavaAssist(mockMonaco(captured))
    const model = { getWordUntilPosition: () => ({ startColumn: 1, endColumn: 1 }) }
    const result = captured.completion!.provideCompletionItems(model, { lineNumber: 1, column: 1 })
    const labels = result.suggestions.map((s) => s.label)
    expect(labels).toContain('onStart')
    expect(labels).toContain('onTick')
    expect(labels).toContain('ctx.emit()')
    expect(labels.length).toBeGreaterThan(3)
  })

  it('hover returns info for a known SDK member', () => {
    const captured: Captured = {}
    registerJavaAssist(mockMonaco(captured))
    const model = { getWordAtPosition: () => ({ word: 'onStart' }) }
    const hover = captured.hover!.provideHover(model, { lineNumber: 1, column: 1 })
    expect(hover).toBeTruthy()
  })
})
