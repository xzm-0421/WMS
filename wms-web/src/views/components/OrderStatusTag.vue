<script setup lang="ts">
import { computed } from 'vue'
import { getStatusMeta, type StatusLabelOptions } from '@/utils/status'

const props = withDefaults(
  defineProps<{
    status?: string | number | null
    pendingLabel?: string
    openLabel?: string
    failedLabel?: string
    partialLabel?: string
  }>(),
  {
    status: '',
    pendingLabel: '待审核',
    openLabel: '待收货',
    failedLabel: undefined,
    partialLabel: undefined,
  },
)

const options = computed<StatusLabelOptions>(() => ({
  pendingLabel: props.pendingLabel,
  openLabel: props.openLabel,
  failedLabel: props.failedLabel,
  partialLabel: props.partialLabel,
}))

const meta = computed(() => getStatusMeta(props.status, options.value))
</script>

<template>
  <el-tag :type="meta.type" size="small">{{ meta.label }}</el-tag>
</template>
