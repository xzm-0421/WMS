<script setup lang="ts">
import { computed } from 'vue'

interface DetailTab {
  name: string
  label: string
  hidden?: boolean
}

const props = defineProps<{ modelValue: string; tabs: DetailTab[] }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()

const visibleTabs = computed(() => props.tabs.filter((tab) => !tab.hidden))
</script>

<template>
  <el-tabs :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)">
    <el-tab-pane
      v-for="tab in visibleTabs"
      :key="tab.name"
      :label="tab.label"
      :name="tab.name"
    >
      <slot :name="tab.name" />
    </el-tab-pane>
  </el-tabs>
</template>
