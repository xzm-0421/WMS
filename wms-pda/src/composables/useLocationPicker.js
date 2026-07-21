import { ref, reactive, watch, onMounted } from 'vue'
import { allocateLocation, listLocations } from '@/api/location.js'
import { parseBarcodeLocal } from '@/utils/scan.js'

const STRATEGY_LABEL = {
  CONSOLIDATE: '同物料归位',
  EMPTY: '空库位',
  DEFAULT: '默认库位',
  MANUAL: '自选库位',
}

export function useLocationPicker(props, emit) {
  const mode = ref('auto')
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
    if (!props.warehouseCode) return null
    loading.value = true
    try {
      const data = await allocateLocation({
        warehouseCode: props.warehouseCode,
        materialCode: props.materialCode || undefined,
        batchNo: props.batchNo || undefined,
        autoAllocate: mode.value === 'auto',
        manualLocationCode: mode.value === 'manual' ? manualCode.value : undefined,
      })
      recommended.locationCode = data.locationCode || ''
      recommended.message = data.message || STRATEGY_LABEL[data.strategy] || ''
      recommended.strategy = data.strategy || ''
      emitChange()
      return data
    } catch {
      recommended.locationCode = ''
      recommended.message = '仅仓库维度'
      emitChange()
      return null
    } finally {
      loading.value = false
    }
  }

  async function loadLocationList() {
    if (!props.warehouseCode) return
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
    emitChange()
    if (m === 'auto') fetchAllocate()
  }

  function onPickerChange(e) {
    const idx = Number(e.detail.value)
    const picked = locationOptions.value[idx]
    if (picked) {
      manualCode.value = picked.code
      manualLabel.value = picked.label
      fetchAllocate()
    }
  }

  function onManualInput() {
    manualLabel.value = manualCode.value
    if (manualCode.value) fetchAllocate()
    else emitChange()
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
          fetchAllocate()
        }
      },
      fail: () => uni.showToast({ title: '扫码取消', icon: 'none' }),
    })
  }

  function emitChange() {
    emit('change', {
      mode: mode.value,
      autoAllocate: mode.value === 'auto',
      locationCode: mode.value === 'manual' ? (manualCode.value || '').trim() : '',
      recommendedLocation: recommended.locationCode,
      strategy: recommended.strategy,
    })
  }

  function getPayload() {
    return {
      autoAllocateLocation: mode.value === 'auto',
      targetLocation: mode.value === 'manual' ? (recommended.locationCode || manualCode.value || '').trim() : undefined,
      locationCode: mode.value === 'manual' ? (recommended.locationCode || manualCode.value || '').trim() : undefined,
    }
  }

  watch(
    () => [props.warehouseCode, props.materialCode, props.batchNo],
    () => {
      if (mode.value === 'auto') fetchAllocate()
      loadLocationList()
    },
  )

  onMounted(() => {
    fetchAllocate()
    loadLocationList()
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
    scanLocation,
    getPayload,
  }
}
