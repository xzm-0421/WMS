<script setup lang="ts">
import { computed, ref } from 'vue'
import { Monitor, Warning } from '@element-plus/icons-vue'
import {
  AUTO_CODE_RULES,
  FLOW_GAPS,
  FLOW_MODULES,
  INVENTORY_TX_TYPES,
  OVERVIEW_MODULES,
  PDA_FEATURES,
} from '@/config/businessFlowData'
import { getStatusMeta } from '@/utils/status'

const activeSection = ref('overview')

const sectionNav = computed(() => [
  { id: 'overview', title: '总体架构' },
  { id: 'auto-codes', title: '编码规则' },
  ...FLOW_MODULES.map((m) => ({ id: m.id, title: m.title })),
  { id: 'pda', title: 'PDA 功能对照' },
  ...(FLOW_GAPS.length ? [{ id: 'gaps', title: '已知缺口' }] : []),
])

function scrollTo(id: string) {
  activeSection.value = id
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function statusTag(status: string, options?: { openLabel?: string; pendingLabel?: string }) {
  const meta = getStatusMeta(status, options)
  return meta
}
</script>

<template>
  <div class="business-flow-page">
    <aside class="flow-nav">
      <div class="nav-title">业务流程手册</div>
      <el-scrollbar class="nav-scroll">
        <button
          v-for="item in sectionNav"
          :key="item.id"
          type="button"
          class="nav-item"
          :class="{ active: activeSection === item.id }"
          @click="scrollTo(item.id)"
        >
          {{ item.title }}
        </button>
      </el-scrollbar>
    </aside>

    <el-scrollbar class="flow-content">
      <div class="flow-body">
        <header class="page-header">
          <h1>业务流程与状态流转</h1>
          <p>基于当前 WMS Web 出入库管理边界整理，涵盖 Web 与 PDA 端操作及单据状态变化。</p>
        </header>

        <!-- 总体架构 -->
        <section id="overview" class="flow-section">
          <h2>一、总体架构</h2>
          <div class="overview-grid">
            <el-card v-for="block in OVERVIEW_MODULES" :key="block.group" shadow="never" class="overview-card">
              <template #header>
                <span class="card-head">{{ block.group }}</span>
              </template>
              <div class="chip-list">
                <el-tag v-for="item in block.items" :key="item" type="info" effect="plain">{{ item }}</el-tag>
              </div>
            </el-card>
          </div>
        <el-table
          :data="[
            { endpoint: 'Web', duty: '主数据、单据创建/审批、查询、打印、看板' },
            { endpoint: 'PDA', duty: '扫码入出库、盘点、快速入库' },
          ]"
          border
          class="端分工-table"
        >
          <el-table-column prop="endpoint" label="端" width="100">
            <template #default="{ row }">
              <el-tag :type="row.endpoint === 'Web' ? 'primary' : 'success'" effect="plain">
                {{ row.endpoint }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="duty" label="主要职责" />
        </el-table>
          <div class="inventory-types">
            <span class="label">库存事务类型：</span>
            <el-tag v-for="t in INVENTORY_TX_TYPES" :key="t" size="small" effect="plain" class="tx-tag">{{ t }}</el-tag>
          </div>
        </section>

        <!-- 编码规则 -->
        <section id="auto-codes" class="flow-section">
          <h2>编码自动生成规则</h2>
          <p class="section-desc">业务单号保存时自动生成；基础资料编码新建时可留空；BOM/物料主数据以金蝶云星空为准。</p>
          <el-table :data="AUTO_CODE_RULES" border stripe size="small">
            <el-table-column prop="prefix" label="前缀/来源" width="120" />
            <el-table-column prop="name" label="对象" width="120" />
            <el-table-column prop="source" label="说明" min-width="280" />
          </el-table>
        </section>

        <!-- 各模块 -->
        <section
          v-for="mod in FLOW_MODULES"
          :id="mod.id"
          :key="mod.id"
          class="flow-section"
        >
          <h2>{{ mod.title }}<span v-if="mod.docCode" class="doc-code">{{ mod.docCode }}</span></h2>
          <p v-if="mod.subtitle" class="section-desc">{{ mod.subtitle }}</p>

          <div class="meta-row">
            <div v-if="mod.webMenu" class="meta-item">
              <el-icon><Monitor /></el-icon>
              <span>Web：{{ mod.webMenu }}</span>
            </div>
            <div v-if="mod.pdaMenu" class="meta-item">
              <el-icon><Connection /></el-icon>
              <span>PDA：{{ mod.pdaMenu }}</span>
            </div>
          </div>

          <h3 class="sub-title">状态流转</h3>
          <div class="transition-list">
            <div v-for="(tr, i) in mod.transitions" :key="i" class="transition-item">
              <template v-if="tr.from">
                <el-tag :type="statusTag(tr.from).type" effect="light" size="small">
                  {{ statusTag(tr.from).label }}
                </el-tag>
                <span class="arrow">→</span>
              </template>
              <el-tag :type="statusTag(tr.to).type" effect="light" size="small">
                {{ statusTag(tr.to).label }}
              </el-tag>
              <span v-if="tr.label" class="transition-label">{{ tr.label }}</span>
            </div>
          </div>

          <h3 class="sub-title">操作步骤</h3>
          <el-table :data="mod.steps" border stripe size="small" class="steps-table">
            <el-table-column prop="order" label="#" width="48" align="center" />
            <el-table-column prop="action" label="操作" min-width="160" />
            <el-table-column prop="channel" label="端" width="88" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="row.channel === 'Web' ? 'primary' : row.channel === 'PDA' ? 'success' : 'info'"
                  size="small"
                  effect="plain"
                >
                  {{ row.channel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="statusChange" label="状态变化" min-width="140">
              <template #default="{ row }">
                {{ row.statusChange || '—' }}
              </template>
            </el-table-column>
            <el-table-column prop="inventory" label="库存影响" min-width="120">
              <template #default="{ row }">
                {{ row.inventory || '—' }}
              </template>
            </el-table-column>
            <el-table-column prop="note" label="备注" min-width="120">
              <template #default="{ row }">
                {{ row.note || '—' }}
              </template>
            </el-table-column>
          </el-table>
        </section>

        <!-- PDA -->
        <section id="pda" class="flow-section">
          <h2>PDA 功能对照</h2>
          <el-table :data="PDA_FEATURES" border stripe size="small">
            <el-table-column prop="name" label="PDA 功能" width="120" />
            <el-table-column prop="biz" label="对应业务" min-width="160" />
            <el-table-column prop="api" label="关键 API" min-width="280">
              <template #default="{ row }">
                <code class="api-code">{{ row.api }}</code>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <!-- 缺口 -->
        <section v-if="FLOW_GAPS.length" id="gaps" class="flow-section">
          <h2>已知流程缺口</h2>
          <el-alert type="warning" :closable="false" show-icon class="gap-alert">
            <template #title>以下为实现层面的已知差异，便于后续完善</template>
          </el-alert>
          <ul class="gap-list">
            <li v-for="gap in FLOW_GAPS" :key="gap.module">
              <el-icon class="gap-icon"><Warning /></el-icon>
              <strong>{{ gap.module }}：</strong>{{ gap.desc }}
            </li>
          </ul>
        </section>
      </div>
    </el-scrollbar>
  </div>
</template>

<style scoped>
.business-flow-page {
  display: flex;
  gap: 16px;
  height: calc(100vh - var(--wms-header-height) - 40px);
  margin: -20px -24px -24px;
}

.flow-nav {
  flex: 0 0 200px;
  background: #fff;
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
}

.nav-title {
  padding: 16px 16px 12px;
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.nav-scroll {
  flex: 1;
}

.nav-item {
  display: block;
  width: 100%;
  padding: 10px 16px;
  border: none;
  background: none;
  text-align: left;
  font-size: 13px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.nav-item:hover {
  background: var(--el-fill-color-light);
  color: var(--el-color-primary);
}

.nav-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 500;
}

.flow-content {
  flex: 1;
  background: var(--wms-page-bg);
}

.flow-body {
  max-width: 960px;
  padding: 24px 32px 48px;
}

.page-header h1 {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
}

.page-header p {
  margin: 0 0 24px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.flow-section {
  margin-bottom: 40px;
  scroll-margin-top: 16px;
}

.flow-section h2 {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
  padding-bottom: 8px;
  border-bottom: 2px solid var(--el-color-primary-light-7);
}

.doc-code {
  margin-left: 8px;
  font-size: 13px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.section-desc {
  margin: -8px 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.overview-card :deep(.el-card__header) {
  padding: 10px 14px;
}

.card-head {
  font-weight: 600;
  font-size: 13px;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.端分工-table {
  margin-bottom: 16px;
}

.inventory-types {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.inventory-types .label {
  color: var(--el-text-color-secondary);
}

.tx-tag {
  font-family: ui-monospace, monospace;
}

.chain-flow {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  border: 1px solid var(--el-border-color-lighter);
}

.chain-node {
  text-align: center;
}

.chain-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-bottom: 8px;
}

.chain-codes {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.chain-arrow {
  text-align: center;
  color: var(--el-text-color-placeholder);
  padding: 6px 0;
  font-size: 18px;
}

.meta-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sub-title {
  margin: 16px 0 10px;
  font-size: 14px;
  font-weight: 600;
}

.transition-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 8px;
}

.transition-item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
}

.transition-item .arrow {
  color: var(--el-text-color-placeholder);
}

.transition-label {
  color: var(--el-text-color-secondary);
  margin-left: 4px;
}

.steps-table {
  margin-bottom: 8px;
}

.api-code {
  font-size: 12px;
  color: var(--el-color-primary);
}

.gap-alert {
  margin-bottom: 16px;
}

.gap-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.gap-list li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  background: #fff;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
  line-height: 1.5;
}

.gap-icon {
  color: var(--el-color-warning);
  margin-top: 2px;
  flex-shrink: 0;
}

@media (max-width: 900px) {
  .business-flow-page {
    flex-direction: column;
    height: auto;
  }

  .flow-nav {
    flex: none;
    max-height: 160px;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
