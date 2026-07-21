import { ref } from 'vue'
import type { Material } from '@/api/material'

const visible = ref(false)
let pendingResolve: ((value: Material | null) => void) | null = null

export function openMaterialPicker(): Promise<Material | null> {
  visible.value = true
  return new Promise((resolve) => {
    pendingResolve = resolve
  })
}

export function resolveMaterialPicker(material: Material | null) {
  visible.value = false
  pendingResolve?.(material)
  pendingResolve = null
}

export function useMaterialPickerState() {
  return { visible }
}
