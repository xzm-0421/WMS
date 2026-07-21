<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import {
  getInboundStatistics,
  getOutboundStatistics,
  getLowStockReport,
  type ReportStatistics,
} from '@/api/report'

const loading = ref(false)
const inboundRef = ref<HTMLDivElement>()
const outboundRef = ref<HTMLDivElement>()
let inboundChart: echarts.ECharts | null = null
let outboundChart: echarts.ECharts | null = null

const lowStockData = ref<Record<string, unknown>[]>([])
const lowStockTotal = ref(0)
const query = reactive({ current: 1, size: 10 })

async function loadCharts() {
  loading.value = true
  try {
    const [inbound, outbound] = await Promise.all([
      getInboundStatistics(),
      getOutboundStatistics(),
    ])
    updateInboundChart(inbound)
    updateOutboundChart(outbound)
  } catch {
    updateInboundChart({ labels: [], inbound: [], outbound: [] })
    updateOutboundChart({ labels: [], inbound: [], outbound: [] })
  } finally {
    loading.value = false
  }
}

async function loadLowStock() {
  try {
    const res = await getLowStockReport(query) as { records?: Record<string, unknown>[]; total?: number }
    lowStockData.value = res.records ?? []
    lowStockTotal.value = res.total ?? 0
  } catch {
    lowStockData.value = []
    lowStockTotal.value = 0
  }
}

function updateInboundChart(data: ReportStatistics) {
  if (!inboundRef.value) return
  if (!inboundChart) inboundChart = echarts.init(inboundRef.value)
  inboundChart.setOption({
    color: ['#d4a574'],
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 24, top: 24, bottom: 32 },
    xAxis: { type: 'category', data: data.labels, axisLabel: { color: '#909399' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f2f5', type: 'dashed' } } },
    series: [{ name: '入库量', type: 'bar', data: data.inbound, barWidth: '40%', itemStyle: { borderRadius: [4, 4, 0, 0] } }],
  })
}

function updateOutboundChart(data: ReportStatistics) {
  if (!outboundRef.value) return
  if (!outboundChart) outboundChart = echarts.init(outboundRef.value)
  outboundChart.setOption({
    color: ['#3b82f6'],
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 24, top: 24, bottom: 32 },
    xAxis: { type: 'category', data: data.labels, axisLabel: { color: '#909399' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f2f5', type: 'dashed' } } },
    series: [{ name: '出库量', type: 'bar', data: data.outbound, barWidth: '40%', itemStyle: { borderRadius: [4, 4, 0, 0] } }],
  })
}

function handleResize() {
  inboundChart?.resize()
  outboundChart?.resize()
}

onMounted(() => {
  loadCharts()
  loadLowStock()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  inboundChart?.dispose()
  outboundChart?.dispose()
})
</script>

<template>
  <div v-loading="loading" class="report-overview">
    <header class="page-header">
      <h1 class="page-title">报表概览</h1>
      <p class="page-subtitle">出入库统计与库存分析</p>
    </header>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="card-title">入库统计</span>
          </template>
          <div ref="inboundRef" class="chart-box" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="card-title">出库统计</span>
          </template>
          <div ref="outboundRef" class="chart-box" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <span class="card-title">低库存预警报表</span>
      </template>
      <el-table :data="lowStockData" stripe>
        <el-table-column prop="warehouseCode" label="仓库" width="100" />
        <el-table-column prop="materialCode" label="物料编码" width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="160" />
        <el-table-column prop="currentQty" label="当前库存" width="110" />
        <el-table-column prop="safetyQty" label="安全库存" width="110" />
        <el-table-column prop="shortageQty" label="缺口数量" width="110" />
      </el-table>
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="lowStockTotal"
        layout="total, prev, pager, next"
        style="margin-top: 16px"
        @current-change="loadLowStock"
      />
    </el-card>
  </div>
</template>

<style scoped>
.report-overview {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--wms-text-primary);
}
.page-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--wms-text-secondary);
}
.card-title {
  font-weight: 600;
  color: var(--wms-text-primary);
}
.chart-card {
  margin-bottom: 16px;
}
.chart-box {
  width: 100%;
  height: 300px;
}
</style>
