interface Props {
  values: number[]
  max?: number
  width?: number
  height?: number
  color?: string
}

/** 의존성 없는 SVG 라인 스파크라인(시계열 메트릭 시각화). */
export function Sparkline({ values, max, width = 130, height = 34, color = '#58a6ff' }: Props) {
  if (values.length === 0) {
    return <svg width={width} height={height} className="sparkline" />
  }
  const m = max ?? Math.max(...values, 1)
  const denom = m <= 0 ? 1 : m
  const step = values.length > 1 ? width / (values.length - 1) : width
  const points = values
    .map((v, i) => `${(i * step).toFixed(1)},${(height - (v / denom) * height).toFixed(1)}`)
    .join(' ')
  return (
    <svg width={width} height={height} className="sparkline" preserveAspectRatio="none">
      <polyline points={points} fill="none" stroke={color} strokeWidth={1.5} />
    </svg>
  )
}
