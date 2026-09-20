const STORAGE_KEY = 'mes_tablet_display'

export const TABLET_PRESETS = [
  { id: 'fit', label: '自适应屏幕', width: 0, height: 0 },
  { id: '1920x1080', label: '1920 × 1080', width: 1920, height: 1080 },
  { id: '1600x900', label: '1600 × 900', width: 1600, height: 900 },
  { id: '1366x768', label: '1366 × 768', width: 1366, height: 768 },
  { id: '1280x800', label: '1280 × 800', width: 1280, height: 800 },
  { id: '1024x768', label: '1024 × 768', width: 1024, height: 768 },
  { id: 'custom', label: '自定义', width: 1280, height: 800 },
]

export function loadTabletDisplay() {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY)
    if (!raw) {
      return { presetId: 'fit', customWidth: 1280, customHeight: 800 }
    }
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    return {
      presetId: parsed.presetId || 'fit',
      customWidth: Number(parsed.customWidth) > 0 ? Number(parsed.customWidth) : 1280,
      customHeight: Number(parsed.customHeight) > 0 ? Number(parsed.customHeight) : 800,
    }
  } catch {
    return { presetId: 'fit', customWidth: 1280, customHeight: 800 }
  }
}

export function saveTabletDisplay(data) {
  uni.setStorageSync(STORAGE_KEY, {
    presetId: data.presetId || 'fit',
    customWidth: Number(data.customWidth) > 0 ? Number(data.customWidth) : 1280,
    customHeight: Number(data.customHeight) > 0 ? Number(data.customHeight) : 800,
  })
}

export function resolveTabletCanvas(display, windowWidth, windowHeight) {
  const fit = display.presetId === 'fit'
  const preset = TABLET_PRESETS.find((item) => item.id === display.presetId) || TABLET_PRESETS[0]
  const width = fit
    ? windowWidth
    : display.presetId === 'custom'
      ? display.customWidth
      : preset.width
  const height = fit
    ? windowHeight
    : display.presetId === 'custom'
      ? display.customHeight
      : preset.height
  const scale = fit ? 1 : Math.min(windowWidth / width, windowHeight / height)
  return { fit, width, height, scale }
}
