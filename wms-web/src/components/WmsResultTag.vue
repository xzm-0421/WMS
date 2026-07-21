<script setup lang="ts">
import { computed } from 'vue'
import { getResultMeta } from '@/utils/status'

const props = defineProps<{
  status?: string | null
}>()

const meta = computed(() => getResultMeta(props.status))
</script>

<template>
  <el-tag :type="meta.type" size="small">{{ meta.label }}</el-tag>
</template>
