import type * as Monaco from 'monaco-editor'

/**
 * SDK API를 시드로 한 경량 Java 자동완성/호버. JDT LS(풀 시맨틱)가 연동되기 전에도
 * onStart/onTick/onEnd 및 ScriptContext 멤버를 보조한다. (Phase 5 스파이크 보완)
 */
interface Member {
  label: string
  insert: string
  doc: string
  kind: 'method' | 'snippet'
}

const LIFECYCLE: Member[] = [
  { label: 'onStart', insert: 'public void onStart() {\n    $0\n}', doc: '최초 실행 시 정확히 1회 호출됩니다.', kind: 'snippet' },
  { label: 'onTick', insert: 'public void onTick() {\n    $0\n}', doc: '지정한 실행 주기마다 반복 호출됩니다. 짧고 논블로킹으로 작성하세요.', kind: 'snippet' },
  { label: 'onEnd', insert: 'public void onEnd() {\n    $0\n}', doc: '종료/중지 시 정확히 1회 호출됩니다(best-effort).', kind: 'snippet' },
]

const CONTEXT: Member[] = [
  { label: 'ctx.log()', insert: 'ctx.log().info("$0")', doc: '구조적 로깅 파사드. 텔레메트리로 대시보드에 스트리밍됩니다.', kind: 'method' },
  { label: 'ctx.param()', insert: 'ctx.param("$1", String.class)', doc: '실행 파라미터 조회: <T> T param(String key, Class<T> type)', kind: 'method' },
  { label: 'ctx.emit()', insert: 'ctx.emit("$1", $2)', doc: '출력 포트로 메시지 emit → 하류 노드로 라우팅(플로우).', kind: 'method' },
  { label: 'ctx.onMessage()', insert: 'ctx.onMessage("$1", msg -> {\n    $0\n})', doc: '입력 포트의 상류 메시지 핸들러 등록.', kind: 'method' },
  { label: 'ctx.state()', insert: 'ctx.state()', doc: '재시작 간 유지되는 키-값 상태 저장소.', kind: 'method' },
]

const SCAFFOLD: Member = {
  label: 'maestro:script',
  insert: [
    'import io.maestro.sdk.Script;',
    '',
    'public class ${1:MyScript} extends Script {',
    '    @Override public void onStart() {',
    '        ctx.log().info("start");',
    '    }',
    '    @Override public void onTick() {',
    '        $0',
    '    }',
    '    @Override public void onEnd() {',
    '        ctx.log().info("end");',
    '    }',
    '}',
  ].join('\n'),
  doc: 'Maestro 스크립트 기본 골격을 삽입합니다.',
  kind: 'snippet',
}

export function registerJavaAssist(monaco: typeof Monaco): void {
  monaco.languages.registerCompletionItemProvider('java', {
    triggerCharacters: ['.'],
    provideCompletionItems(model, position) {
      const word = model.getWordUntilPosition(position)
      const range: Monaco.IRange = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      }
      const all = [SCAFFOLD, ...LIFECYCLE, ...CONTEXT]
      const suggestions: Monaco.languages.CompletionItem[] = all.map((m) => ({
        label: m.label,
        kind:
          m.kind === 'snippet'
            ? monaco.languages.CompletionItemKind.Snippet
            : monaco.languages.CompletionItemKind.Method,
        insertText: m.insert,
        insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
        documentation: { value: m.doc },
        detail: 'Maestro SDK',
        range,
      }))
      return { suggestions }
    },
  })

  monaco.languages.registerHoverProvider('java', {
    provideHover(model, position) {
      const wordInfo = model.getWordAtPosition(position)
      if (!wordInfo) {
        return null
      }
      const member = [...LIFECYCLE, ...CONTEXT].find(
        (m) => m.label === wordInfo.word || m.label.startsWith('ctx.' + wordInfo.word),
      )
      if (!member) {
        return null
      }
      return {
        contents: [{ value: '**' + member.label + '** — Maestro SDK' }, { value: member.doc }],
      }
    },
  })
}
