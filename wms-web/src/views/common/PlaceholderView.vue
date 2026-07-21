<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <div class="placeholder">
    <el-empty :description="`${route.meta.title || '功能'} 开发中`" />
  </div>
</template>

<style scoped>
.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  background: var(--wms-card-bg);
  border-radius: var(--wms-card-radius);
  box-shadow: var(--wms-card-shadow);
}
</style>
