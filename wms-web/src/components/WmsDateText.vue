<script setup lang="ts">
import { computed } from 'vue'
import { formatDate } from '@/utils/format'

const props = defineProps<{
  value?: string | number | Date | null
}>()

const text = computed(() => formatDate(props.value))
</script>

<template>
  <span>{{ text }}</span>
</template>
