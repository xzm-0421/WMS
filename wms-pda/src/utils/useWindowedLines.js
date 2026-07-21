import { computed, ref, watch } from 'vue'

/**
 * 长列表窗口化：只渲染可视附近行，降低 PDA 节点数。
 * @param {import('vue').Ref|import('vue').ComputedRef} linesRef
 * @param {{ windowSize?: number, rowHeightPx?: number }} [options]
 */
export function useWindowedLines(linesRef, options = {}) {
  const windowSize = options.windowSize || 36
  const rowHeightPx = options.rowHeightPx || 140
  const scrollTop = ref(0)
  const pinnedLineNo = ref(null)

  const windowed = computed(() => {
    const all = linesRef.value || []
    if (all.length <= windowSize + 8) {
      return { items: all, offset: 0, total: all.length, padTop: 0, padBottom: 0 }
    }
    let start = Math.floor(scrollTop.value / rowHeightPx) - 4
    if (start < 0) start = 0
    let end = start + windowSize
    if (end > all.length) {
      end = all.length
      start = Math.max(0, end - windowSize)
    }
    if (pinnedLineNo.value != null) {
      const idx = all.findIndex((l) => l.lineNo === pinnedLineNo.value)
      if (idx >= 0 && (idx < start || idx >= end)) {
        start = Math.max(0, idx - Math.floor(windowSize / 2))
        end = Math.min(all.length, start + windowSize)
        start = Math.max(0, end - windowSize)
      }
    }
    const items = all.slice(start, end)
    return {
      items,
      offset: start,
      total: all.length,
      padTop: start * rowHeightPx,
      padBottom: Math.max(0, (all.length - end) * rowHeightPx),
    }
  })

  function onScroll(e) {
    const top = e?.detail?.scrollTop
    if (typeof top === 'number') scrollTop.value = top
  }

  function pinLine(lineNo) {
    pinnedLineNo.value = lineNo
  }

  watch(linesRef, () => {
    if ((linesRef.value || []).length <= windowSize) {
      scrollTop.value = 0
    }
  })

  return { windowed, onScroll, pinLine, scrollTop }
}

export default useWindowedLines
