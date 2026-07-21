<script setup lang="ts">
import { List } from '@element-plus/icons-vue'
import type { Material } from '@/api/material'
import { openMaterialPicker } from '@/composables/useMaterialPicker'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    size?: 'small' | 'default' | 'large'
    disabled?: boolean
    placeholder?: string
  }>(),
  {
    modelValue: '',
    size: 'default',
    disabled: false,
    placeholder: '物料编码',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  select: [material: Material]
}>()

async function handlePick() {
  if (props.disabled) return
  const material = await openMaterialPicker()
  if (!material) return
  emit('update:modelValue', material.materialCode)
  emit('select', material)
}
</script>

<template>
  <el-input
    :model-value="modelValue"
    :size="size"
    :disabled="disabled"
    :placeholder="placeholder"
    clearable
    class="material-select-input"
    @update:model-value="emit('update:modelValue', $event ?? '')"
  >
    <template #prepend>
      <el-button :size="size" :disabled="disabled" title="打开物料列表" @click="handlePick">
        <el-icon><List /></el-icon>
      </el-button>
    </template>
  </el-input>
</template>

<style scoped>
.material-select-input {
  width: 100%;
}
.material-select-input :deep(.el-input-group__prepend) {
  padding: 0 10px;
}
</style>
