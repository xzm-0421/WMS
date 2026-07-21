<script setup lang="ts">
defineOptions({ inheritAttrs: false })

withDefaults(
  defineProps<{
    /** 占满表单项宽度（对话框、筛选栏等） */
    block?: boolean
    /** 下拉面板是否跟随输入框宽度；默认 false 以便完整展示选项文字 */
    fitInputWidth?: boolean
  }>(),
  {
    block: false,
    fitInputWidth: false,
  },
)
</script>

<template>
  <el-select
    v-bind="$attrs"
    class="wms-select"
    :class="{ 'wms-select--block': block }"
    :fit-input-width="fitInputWidth"
    popper-class="wms-select-popper"
  >
    <slot />
  </el-select>
</template>
