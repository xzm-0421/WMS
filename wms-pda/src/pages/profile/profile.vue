<template>
  <ProfilePanel ref="panelRef" />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import ProfilePanel from '@/components/ProfilePanel.vue'

const panelRef = ref(null)
onMounted(() => panelRef.value?.loadProfile?.())
</script>
