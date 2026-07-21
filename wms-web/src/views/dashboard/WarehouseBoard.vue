<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import * as echarts from 'echarts'
import { getWarehouseDashboard } from '@/api/report'
import { useDashboardPoll } from '@/composables/useDashboardPoll'

const gaugeRef = ref<HTMLDivElement>()
const trendRef = ref<HTMLDivElement>()
let gaugeChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null
const stats = ref({ totalStockQty: 0, lowStockCount: 0 })

async function loadData() {
  const data = await getWarehouseDashboard()
  stats.value.totalStockQty = Number(data.totalStockQty ?? 0)
  stats.value.lowStockCount = Number(data.lowStockCount ?? 0)
  const util = (data.warehouseUtilization as Record<string, unknown>[]) ?? []
  renderGauge(util)
  const inbound = data.inboundTrend as { labels: string[]; inbound: number[] }
  const outbound = data.outboundTrend as { outbound: number[] }
  renderTrend(inbound?.labels ?? [], inbound?.inbound ?? [], outbound?.outbound ?? [])
}

function renderGauge(rows: Record<string, unknown>[]) {
  if (!gaugeRef.value) return
  if (!gaugeChart) gaugeChart = echarts.init(gaugeRef.value)
  const names = rows.map((r) => String(r.warehouseName ?? r.warehouseCode))
  const values = rows.map((r) => {
    const rated = Number(r.ratedCapacity ?? 0)
    const cur = Number(r.currentStock ?? 0)
    return rated > 0 ? Math.min(100, (cur / rated) * 100) : 0
  })
  gaugeChart.setOption({
    tooltip: {},
    xAxis: { type: 'category', data: names },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    series: [{ type: 'bar', data: values, itemStyle: { color: '#409eff' } }],
  })
}

function renderTrend(labels: string[], inbound: number[], outbound: number[]) {
  if (!trendRef.value) return
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['入库', '出库'] },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value' },
    series: [
      { name: '入库', type: 'line', data: inbound },
      { name: '出库', type: 'line', data: outbound },
    ],
  })
}

useDashboardPoll(loadData)
onBeforeUnmount(() => {
  gaugeChart?.dispose()
  trendChart?.dispose()
})
</script>

<template>
  <el-row :gutter="16" class="mb-16">
    <el-col :span="8"><el-statistic title="当前库存总量" :value="stats.totalStockQty" /></el-col>
    <el-col :span="8"><el-statistic title="低库存预警 SKU" :value="stats.lowStockCount" /></el-col>
  </el-row>
  <el-row :gutter="16">
    <el-col :span="12"><el-card shadow="never"><div ref="trendRef" style="height:320px" /></el-card></el-col>
    <el-col :span="12"><el-card shadow="never" header="仓库空间使用率"><div ref="gaugeRef" style="height:320px" /></el-card></el-col>
  </el-row>
</template>

<style scoped>.mb-16 { margin-bottom: 16px; }</style>
