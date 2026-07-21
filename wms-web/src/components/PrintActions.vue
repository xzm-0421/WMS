<script setup lang="ts">
import { openWmsPrint } from '@/utils/print'
import type { PrintBiz } from '@/config/printConfig'

const props = defineProps<{
  biz: PrintBiz
  docNo: string
}>()

/** 打开预览页，用户在套打页内自行点击「打印」 */
function handlePrint() {
  openWmsPrint(props.biz, props.docNo)
}
</script>

<template>
  <el-button link type="primary" @click="handlePrint">打印</el-button>
</template>
