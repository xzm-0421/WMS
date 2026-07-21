<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { useUserStore } from '@/stores/user'
import { Document, Goods, List, Timer } from '@element-plus/icons-vue'
import { getDashboardStats, type DashboardStats, type InventoryWarningItem, type OperationLogItem } from '@/api/report'
import { useDashboardPoll } from '@/composables/useDashboardPoll'

const userStore = useUserStore()
const trendRef = ref<HTMLDivElement>()
const pieRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null

const stats = ref([
  {
    title: '今日入库单数',
    value: 0,
    icon: Document,
    bg: 'var(--wms-stat-pink-bg)',
    color: 'var(--wms-stat-pink-icon)',
  },
  {
    title: '今日出库单数',
    value: 0,
    icon: List,
    bg: 'var(--wms-stat-orange-bg)',
    color: 'var(--wms-stat-orange-icon)',
  },
  {
    title: '库存 SKU 数',
    value: 0,
    icon: Goods,
    bg: 'var(--wms-stat-blue-bg)',
    color: 'var(--wms-stat-blue-icon)',
  },
  {
    title: '待处理任务数',
    value: 0,
    icon: Timer,
    bg: 'var(--wms-stat-yellow-bg)',
    color: 'var(--wms-stat-yellow-icon)',
  },
])

const warnings = ref<InventoryWarningItem[]>([])
const recentLogs = ref<OperationLogItem[]>([])

const DEMO_STATS: DashboardStats = {
  todayInboundCount: 0,
  todayOutboundCount: 0,
  skuCount: 0,
  pendingTaskCount: 0,
  weeklyTrend: {
    labels: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
    inbound: [0, 0, 0, 0, 0, 0, 0],
    outbound: [0, 0, 0, 0, 0, 0, 0],
  },
  warehouseDistribution: [
    { name: '原料仓', value: 0 },
    { name: '成品仓', value: 0 },
    { name: '辅料仓', value: 0 },
    { name: '其他', value: 0 },
  ],
  warnings: [],
  recentLogs: [],
}

function applyDashboardData(data: DashboardStats) {
  stats.value[0]!.value = data.todayInboundCount
  stats.value[1]!.value = data.todayOutboundCount
  stats.value[2]!.value = data.skuCount
  stats.value[3]!.value = data.pendingTaskCount
  warnings.value = data.warnings
  recentLogs.value = data.recentLogs
  updateTrendChart(data.weeklyTrend.labels, data.weeklyTrend.inbound, data.weeklyTrend.outbound)
  updatePieChart(data.warehouseDistribution)
}

function updateTrendChart(labels: string[], inbound: number[], outbound: number[]) {
  if (!trendRef.value) return
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    color: ['#d4a574', '#3b82f6'],
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['入库', '出库'],
      right: 16,
      top: 0,
      textStyle: { color: '#909399', fontSize: 12 },
    },
    grid: { left: 48, right: 24, top: 40, bottom: 32 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: labels,
      axisLine: { lineStyle: { color: '#e4e7ed' } },
      axisLabel: { color: '#909399' },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f0f2f5', type: 'dashed' } },
      axisLabel: { color: '#909399' },
    },
    series: [
      {
        name: '入库',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: inbound,
        lineStyle: { width: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(212, 165, 116, 0.25)' },
            { offset: 1, color: 'rgba(212, 165, 116, 0.02)' },
          ]),
        },
      },
      {
        name: '出库',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: outbound,
        lineStyle: { width: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(59, 130, 246, 0.2)' },
            { offset: 1, color: 'rgba(59, 130, 246, 0.02)' },
          ]),
        },
      },
    ],
  })
}

function updatePieChart(distribution: { name: string; value: number }[]) {
  if (!pieRef.value) return
  if (!pieChart) pieChart = echarts.init(pieRef.value)
  pieChart.setOption({
    color: ['#d4a574', '#3b82f6', '#10b981', '#f97316'],
    tooltip: { trigger: 'item', formatter: '{b}: {d}%' },
    legend: {
      orient: 'vertical',
      right: 8,
      top: 'center',
      textStyle: { color: '#909399', fontSize: 12 },
    },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['38%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        data: distribution,
      },
    ],
  })
}

async function loadDashboard() {
  try {
    const data = await getDashboardStats()
    applyDashboardData(data)
  } catch {
    applyDashboardData(DEMO_STATS)
  }
}

function handleResize() {
  trendChart?.resize()
  pieChart?.resize()
}

onMounted(async () => {
  if (!userStore.userInfo) {
    await userStore.fetchUserInfo()
  }
  window.addEventListener('resize', handleResize)
})

useDashboardPoll(loadDashboard)

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  pieChart?.dispose()
})
</script>

<template>
  <div class="dashboard">
    <header class="page-header">
      <h1 class="page-title">工作台</h1>
      <p class="page-subtitle">WMS 仓储管理数据概览</p>
    </header>

    <el-row :gutter="16" class="stat-row">
      <el-col
        v-for="(item, index) in stats"
        :key="item.title"
        :xs="24"
        :sm="12"
        :lg="6"
      >
        <div class="stat-card" :style="{ animationDelay: `${index * 0.06}s` }">
          <div class="stat-icon" :style="{ background: item.bg, color: item.color }">
            <el-icon :size="22"><component :is="item.icon" /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-value">{{ item.value }}</div>
            <div class="stat-label">{{ item.title }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="16">
        <div class="panel-card chart-panel">
          <div class="panel-header">
            <span class="panel-title">近7天入库 / 出库趋势</span>
          </div>
          <div ref="trendRef" class="chart-box chart-box--trend" />
        </div>
      </el-col>
      <el-col :xs="24" :lg="8">
        <div class="panel-card chart-panel">
          <div class="panel-header">
            <span class="panel-title">各仓库库存占比</span>
          </div>
          <div ref="pieRef" class="chart-box chart-box--pie" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="list-row">
      <el-col :xs="24" :lg="12">
        <div class="panel-card list-panel">
          <div class="panel-header">
            <span class="panel-title">库存预警</span>
          </div>
          <el-table v-if="warnings.length" :data="warnings" size="small" stripe>
            <el-table-column prop="materialCode" label="物料" width="120" />
            <el-table-column prop="materialName" label="名称" min-width="100" show-overflow-tooltip />
            <el-table-column prop="currentQty" label="当前" width="70" />
            <el-table-column prop="safetyQty" label="安全" width="70" />
          </el-table>
          <div v-else class="empty-state">
            <div class="empty-illustration" />
            <p class="empty-text">暂无数据</p>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :lg="12">
        <div class="panel-card list-panel">
          <div class="panel-header">
            <span class="panel-title">最近操作日志</span>
          </div>
          <el-table v-if="recentLogs.length" :data="recentLogs" size="small" stripe>
            <el-table-column prop="operatorName" label="操作人" width="80" />
            <el-table-column prop="module" label="模块" width="80" />
            <el-table-column prop="operationContent" label="内容" min-width="140" show-overflow-tooltip />
            <el-table-column prop="operationTime" label="时间" width="120">
              <template #default="{ row }">
                <WmsDateText :value="row.operationTime" />
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-state">
            <div class="empty-illustration" />
            <p class="empty-text">暂无数据</p>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
}

.page-header {
  margin-bottom: 4px;
}

.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--wms-text-primary);
  line-height: 1.3;
}

.page-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--wms-text-secondary);
}

.stat-row,
.chart-row,
.list-row {
  margin-bottom: 0 !important;
}

.stat-row .el-col {
  margin-bottom: 16px;
}

.chart-row .el-col,
.list-row .el-col {
  margin-bottom: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 22px;
  background: var(--wms-card-bg);
  border-radius: var(--wms-card-radius);
  box-shadow: var(--wms-card-shadow);
  transition:
    transform var(--wms-transition-normal),
    box-shadow var(--wms-transition-normal);
  animation: cardFadeIn 0.45s ease both;
  cursor: default;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--wms-card-shadow-hover);
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--wms-text-primary);
  line-height: 1.2;
}

.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: var(--wms-text-secondary);
}

.panel-card {
  background: var(--wms-card-bg);
  border-radius: var(--wms-card-radius);
  box-shadow: var(--wms-card-shadow);
  transition: box-shadow var(--wms-transition-normal);
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-card:hover {
  box-shadow: var(--wms-card-shadow-hover);
}

.panel-header {
  padding: 16px 20px 0;
  flex-shrink: 0;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--wms-text-primary);
}

.chart-panel {
  min-height: 320px;
}

.chart-box {
  flex: 1;
  width: 100%;
  min-height: 260px;
}

.list-panel {
  min-height: 280px;
}

.list-panel .el-table {
  margin: 8px 16px 16px;
  width: calc(100% - 32px);
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 20px 32px;
}

.empty-illustration {
  width: 120px;
  height: 80px;
  margin-bottom: 12px;
  background: linear-gradient(180deg, #f0f2f5 0%, #e8eaed 100%);
  border-radius: 8px;
  position: relative;
  opacity: 0.7;
}

.empty-illustration::before,
.empty-illustration::after {
  content: '';
  position: absolute;
  background: #dcdfe6;
  border-radius: 2px;
}

.empty-illustration::before {
  width: 60px;
  height: 4px;
  top: 28px;
  left: 30px;
}

.empty-illustration::after {
  width: 40px;
  height: 4px;
  top: 40px;
  left: 40px;
}

.empty-text {
  margin: 0;
  font-size: 13px;
  color: var(--wms-text-placeholder);
}

@keyframes cardFadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1200px) {
  .chart-panel {
    min-height: 300px;
  }
}

@media (max-width: 768px) {
  .page-title {
    font-size: 20px;
  }

  .stat-value {
    font-size: 24px;
  }

  .chart-box--trend {
    min-height: 240px;
  }

  .chart-box--pie {
    min-height: 220px;
  }
}
</style>
