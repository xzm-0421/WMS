import { ref, reactive, watch, onMounted } from 'vue'
import { listWarehouses } from '@/api/warehouse.js'

export function useWarehousePicker(props, emit) {
  const mode = ref('auto')
  const loading = ref(false)
  const warehouseOptions = ref([])
  const manualIndex = ref(-1)
  const recommended = reactive({
    warehouseCode: '',
    erpWarehouseCode: '',
    warehouseName: '',
    label: '',
  })

  function formatLabel(item) {
    if (!item) return ''
    const erp = item.erpWarehouseCode ? ` / ${item.erpWarehouseCode}` : ''
    return `${item.warehouseCode}${erp} · ${item.warehouseName || item.warehouseCode}`
  }

  function applySuggest(code, name) {
    const value = (code || '').trim()
    recommended.warehouseCode = value
    recommended.erpWarehouseCode = value
    recommended.warehouseName = name || value
    recommended.label = value ? `${value}${name ? ` · ${name}` : ''}` : '-'
    emitChange()
  }

  async function loadWarehouseList() {
    loading.value = true
    try {
      const page = await listWarehouses()
      const records = page?.records || page?.list || []
      warehouseOptions.value = records.map((item) => ({
        warehouseCode: item.warehouseCode,
        erpWarehouseCode: item.erpWarehouseCode || item.warehouseCode,
        warehouseName: item.warehouseName || item.warehouseCode,
        label: formatLabel(item),
      }))
      if (mode.value === 'manual' && manualIndex.value < 0 && warehouseOptions.value.length) {
        const suggest = (props.suggestCode || '').trim()
        const idx = warehouseOptions.value.findIndex(
          (w) => w.warehouseCode === suggest || w.erpWarehouseCode === suggest,
        )
        manualIndex.value = idx >= 0 ? idx : 0
        const picked = warehouseOptions.value[manualIndex.value]
        recommended.warehouseCode = picked.warehouseCode
        recommended.erpWarehouseCode = picked.erpWarehouseCode
        recommended.warehouseName = picked.warehouseName
        recommended.label = picked.label
        emitChange()
      }
    } catch {
      warehouseOptions.value = []
    } finally {
      loading.value = false
    }
  }

  function setMode(next) {
    mode.value = next
    if (next === 'auto') {
      applySuggest(props.suggestCode, props.suggestName)
    } else if (!warehouseOptions.value.length) {
      loadWarehouseList()
    } else if (manualIndex.value >= 0) {
      const picked = warehouseOptions.value[manualIndex.value]
      recommended.warehouseCode = picked.warehouseCode
      recommended.erpWarehouseCode = picked.erpWarehouseCode
      recommended.warehouseName = picked.warehouseName
      recommended.label = picked.label
      emitChange()
    }
  }

  function onPickerChange(e) {
    const idx = Number(e.detail.value)
    manualIndex.value = idx
    const picked = warehouseOptions.value[idx]
    if (!picked) return
    recommended.warehouseCode = picked.warehouseCode
    recommended.erpWarehouseCode = picked.erpWarehouseCode
    recommended.warehouseName = picked.warehouseName
    recommended.label = picked.label
    emitChange()
  }

  function emitChange() {
    emit?.('change', getPayload())
  }

  function getPayload() {
    if (mode.value === 'auto') {
      return { autoAssignWarehouse: true }
    }
    return {
      autoAssignWarehouse: false,
      warehouseCode: recommended.warehouseCode,
      erpWarehouseCode: recommended.erpWarehouseCode,
    }
  }

  watch(
    () => [props.suggestCode, props.suggestName],
    () => {
      if (mode.value === 'auto') {
        applySuggest(props.suggestCode, props.suggestName)
      }
    },
    { immediate: true },
  )

  onMounted(() => {
    if (mode.value === 'manual') {
      loadWarehouseList()
    }
  })

  return {
    mode,
    loading,
    warehouseOptions,
    manualIndex,
    recommended,
    setMode,
    loadWarehouseList,
    onPickerChange,
    getPayload,
  }
}

export default useWarehousePicker
