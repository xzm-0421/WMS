import { ref, reactive, watch, onMounted } from 'vue'
import { allocateLocation, listLocations } from '@/api/location.js'
import { parseBarcodeLocal } from '@/utils/scan.js'

const STRATEGY_LABEL = {
  CONSOLIDATE: '同物料归位',
  EMPTY: '空库位',
  DEFAULT: '默认库位',
  MANUAL: '自选库位',
  WAREHOUSE_ONLY: '仅仓库，无库位',
}

/**
 * 入库库位选择：不选库位（仅仓库）/ 自动分配 / 自选库位（均可为空）。
 */
export function useLocationPicker(props, emit) {
  /** none | auto | manual */
  const mode = ref('none')
  const loading = ref(false)
  const manualCode = ref('')
  const manualLabel = ref('')
  const locationOptions = ref([])
  const recommended = reactive({
    locationCode: '',
    locationName: '',
    message: '',
    strategy: '',
  })

  async function fetchAllocate() {
    if (mode.value === 'none') {
      recommended.locationCode = ''
      recommended.message = '仅仓库入库，不指定库位'
      recommended.strategy = 'NONE'
      emitChange()
      return null
    }
    if (!props.warehouseCode) {
      recommended.locationCode = ''
      recommended.message = '请先选择仓库'
      emitChange()
      return null
    }
    loading.value = true
    try {
      const data = await allocateLocation({
        warehouseCode: props.warehouseCode,
        materialCode: props.materialCode || undefined,
        batchNo: props.batchNo || undefined,
        autoAllocate: mode.value === 'auto',
        manualLocationCode: mode.value === 'manual' ? manualCode.value || undefined : undefined,
      })
      recommended.locationCode = data.locationCode || ''
      recommended.message = data.message || STRATEGY_LABEL[data.strategy] || ''
      recommended.strategy = data.strategy || ''
      if (mode.value === 'manual' && !manualCode.value && recommended.locationCode) {
        // 自选但未填时，服务端可能仍返回推荐，展示即可，不强制采用
      }
      emitChange()
      return data
    } catch {
      recommended.locationCode = ''
      recommended.message = mode.value === 'auto' ? '未分配到库位，可仅按仓库入库' : '库位校验失败'
      emitChange()
      return null
    } finally {
      loading.value = false
    }
  }

  async function loadLocationList() {
    if (!props.warehouseCode) {
      locationOptions.value = []
      return
    }
    try {
      const list = await listLocations(props.warehouseCode)
      locationOptions.value = (list || []).map((loc) => ({
        code: loc.locationCode,
        label: `${loc.locationCode}${loc.locationName ? ' · ' + loc.locationName : ''}`,
      }))
    } catch {
      locationOptions.value = []
    }
  }

  function setMode(m) {
    mode.value = m
    if (m === 'none') {
      manualCode.value = ''
      manualLabel.value = ''
      recommended.locationCode = ''
      recommended.message = '仅仓库入库，不指定库位'
      recommended.strategy = 'NONE'
      emitChange()
      return
    }
    if (m === 'auto') {
      fetchAllocate()
      return
    }
    emitChange()
    loadLocationList()
  }

  function onPickerChange(e) {
    const idx = Number(e.detail.value)
    const picked = locationOptions.value[idx]
    if (picked) {
      manualCode.value = picked.code
      manualLabel.value = picked.label
      recommended.locationCode = picked.code
      recommended.message = STRATEGY_LABEL.MANUAL
      recommended.strategy = 'MANUAL'
      emitChange()
    }
  }

  function clearManual() {
    manualCode.value = ''
    manualLabel.value = ''
    recommended.locationCode = ''
    recommended.message = '未选库位，仅按仓库入库'
    recommended.strategy = 'MANUAL_EMPTY'
    emitChange()
  }

  function onManualInput() {
    manualLabel.value = manualCode.value
    const code = (manualCode.value || '').trim()
    if (!code) {
      clearManual()
      return
    }
    recommended.locationCode = code
    recommended.message = STRATEGY_LABEL.MANUAL
    recommended.strategy = 'MANUAL'
    emitChange()
  }

  function scanLocation() {
    uni.scanCode({
      success: (res) => {
        const parsed = parseBarcodeLocal(res.result || '')
        const code = parsed.locationCode || (res.result || '').trim()
        if (code) {
          manualCode.value = code
          manualLabel.value = code
          mode.value = 'manual'
          recommended.locationCode = code
          recommended.message = STRATEGY_LABEL.MANUAL
          recommended.strategy = 'MANUAL'
          emitChange()
        }
      },
      fail: () => uni.showToast({ title: '扫码取消', icon: 'none' }),
    })
  }

  function emitChange() {
    emit('change', getPayload())
  }

  function getPayload() {
    if (mode.value === 'none') {
      return {
        autoAllocateLocation: false,
        locationCode: undefined,
        targetLocation: undefined,
      }
    }
    if (mode.value === 'auto') {
      const code = (recommended.locationCode || '').trim()
      return {
        autoAllocateLocation: true,
        locationCode: code || undefined,
        targetLocation: code || undefined,
      }
    }
    const code = (manualCode.value || recommended.locationCode || '').trim()
    return {
      autoAllocateLocation: false,
      locationCode: code || undefined,
      targetLocation: code || undefined,
    }
  }

  watch(
    () => [props.warehouseCode, props.materialCode, props.batchNo],
    () => {
      loadLocationList()
      if (mode.value === 'auto') fetchAllocate()
    },
  )

  onMounted(() => {
    loadLocationList()
    if (mode.value === 'auto') fetchAllocate()
  })

  return {
    mode,
    loading,
    manualCode,
    manualLabel,
    locationOptions,
    recommended,
    STRATEGY_LABEL,
    setMode,
    fetchAllocate,
    onPickerChange,
    onManualInput,
    clearManual,
    scanLocation,
    getPayload,
  }
}
