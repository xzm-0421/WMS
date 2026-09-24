# YM_MES 系统功能完善 - 具体开发方案

## 方案总览

基于需求差异分析,按优先级分为3个阶段:
- **阶段1**: WMS核心功能接线 (0.5天,立即见效)
- **阶段2**: MES细节优化 (2-3天,提升用户体验)
- **阶段3**: WMS缺失模块补全 (2-4周,功能完整)

---

## 阶段1: WMS核心功能接线 (半天完成)

### 目标
激活3个已完成但未上线的核心功能:出库单管理、移库管理、盘点计划

### 任务1.1: 新增路由配置

**文件**: `wms-web/src/router/index.ts`

**操作**: 在 `children` 数组中,找到合适位置插入以下3条路由

```typescript
// 在 /outbound/pda-records 路由后添加
{
  path: 'outbound/orders',
  name: 'OutboundOrders',
  component: () => import('@/views/outbound/OutboundOrderList.vue'),
  meta: { title: '出库单', permission: MENU_PERMISSIONS['/outbound/orders'] },
},

// 在 /inventory/sample-plans 路由后添加
{
  path: 'inventory/transfers',
  name: 'InventoryTransfers',
  component: () => import('@/views/inventory/TransferList.vue'),
  meta: { title: '移库管理', permission: MENU_PERMISSIONS['/inventory/transfers'] },
},

// 在 /barcode/rules 路由后添加
{
  path: 'stocktake/plans',
  name: 'StocktakePlans',
  component: () => import('@/views/stocktake/PlanList.vue'),
  meta: { title: '盘点计划', permission: MENU_PERMISSIONS['/stocktake/plans'] },
},
```

### 任务1.2: 新增权限映射

**文件**: `wms-web/src/utils/permission.ts`

**操作**: 在 `MENU_PERMISSIONS` 对象中添加3个权限码

```typescript
export const MENU_PERMISSIONS: Record<string, string> = {
  '/dashboard': 'dashboard:view',
  '/system/users': 'system:user:list',
  // ... 现有配置 ...
  
  // 新增以下3行
  '/outbound/orders': 'outbound:list',
  '/inventory/transfers': 'inventory:transfer:list',
  '/stocktake/plans': 'stockcheck:plan:list',
  
  '/mes/materials': 'mes:material:list',
  // ... 现有配置 ...
}
```

### 任务1.3: 菜单配置

**文件**: `wms-web/src/layouts/MainLayout.vue`

**操作**: 修改 `menus` 数组

**1) 在"出入库管理"分组中添加"出库单"菜单**

找到 `index: 'warehouse'` 的菜单项,在 `children` 中添加:

```typescript
{
  index: 'warehouse',
  title: '出入库管理',
  icon: Document,
  children: [
    { path: '/inbound/orders', title: '入库单', permission: MENU_PERMISSIONS['/inbound/orders'] },
    { path: '/inbound/pda-records', title: 'PDA入库记录', permission: MENU_PERMISSIONS['/inbound/pda-records'] },
    { path: '/inbound/receive-batches', title: '收料入库批次', permission: MENU_PERMISSIONS['/inbound/pda-records'] },
    // 新增以下行
    { path: '/outbound/orders', title: '出库单', permission: MENU_PERMISSIONS['/outbound/orders'] },
    { path: '/outbound/pda-records', title: 'PDA出库记录', permission: MENU_PERMISSIONS['/outbound/pda-records'] },
  ],
},
```

**2) 在"库存管理"分组中添加"移库管理"菜单**

找到 `index: 'inventory'` 的菜单项,在 `children` 中添加:

```typescript
{
  index: 'inventory',
  title: '库存管理',
  icon: Goods,
  children: [
    { path: '/inventory/list', title: '实时库存', permission: MENU_PERMISSIONS['/inventory/list'] },
    { path: '/inventory/sample-plans', title: '库存抽检', permission: MENU_PERMISSIONS['/inventory/sample-plans'] },
    // 新增以下行
    { path: '/inventory/transfers', title: '移库管理', permission: MENU_PERMISSIONS['/inventory/transfers'] },
  ],
},
```

**3) 新增"盘点管理"菜单组**

在 `menus` 数组中,在"轻MES"分组前插入:

```typescript
{
  index: 'stocktake',
  title: '盘点管理',
  icon: Grid, // 复用现有图标
  children: [
    { path: '/stocktake/plans', title: '盘点计划', permission: MENU_PERMISSIONS['/stocktake/plans'] },
  ],
},
```

### 任务1.4: 后端权限数据准备

**方式1: SQL直接插入** (如果后端有权限管理表)

```sql
-- 插入权限码
INSERT INTO sys_permission (permission_code, permission_name, resource_type, parent_id) 
VALUES 
('outbound:list', '出库单查询', 'MENU', NULL),
('inventory:transfer:list', '移库管理', 'MENU', NULL),
('stockcheck:plan:list', '盘点计划管理', 'MENU', NULL);

-- 关联到超级管理员角色 (假设角色ID为1)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE permission_code IN ('outbound:list', 'inventory:transfer:list', 'stockcheck:plan:list');
```

**方式2: 通过Web界面配置** (如果有角色管理页面)

访问 `/system/roles`,编辑超级管理员角色,手动勾选新增的3个权限

### 验收测试

**测试用例1: 出库单管理**
1. 登录系统,在左侧菜单"出入库管理"下看到"出库单"菜单项
2. 点击进入 `/outbound/orders`,页面正常加载
3. 查询出库单列表,分页功能正常
4. 点击"新建出库单",填写表单,选择物料,提交成功
5. 点击某条记录"查看详情",显示完整信息
6. 点击"推荐库位",返回FIFO排序的库位列表
7. 测试审核、反审核、取消流程

**测试用例2: 移库管理**
1. 在左侧菜单"库存管理"下看到"移库管理"菜单项
2. 点击进入 `/inventory/transfers`,页面正常加载
3. 点击"新建调拨",填写源库位、目标库位、物料、数量,提交成功
4. 查看调拨单列表,状态为"待审批"
5. 点击"审批",状态变为"执行中"
6. 点击"执行",状态变为"已完成"
7. 查询库存,确认源库位减少、目标库位增加

**测试用例3: 盘点计划**
1. 在左侧菜单看到"盘点管理"菜单组,下有"盘点计划"菜单项
2. 点击进入 `/stocktake/plans`,页面正常加载
3. 点击"新建计划",填写计划名称、选择仓库、盘点类型(全盘/动态盘点)
4. 提交后列表显示新记录,状态为"草稿"
5. 点击"发布",状态变为"已发布"
6. 通过PDA端验证: 登录PDA,进入盘点任务列表,可以看到刚发布的计划

---

## 阶段2: MES细节优化 (2-3天)

### 任务2.1: 工序计划列表增强 (4小时)

**目标**: 显示同步状态、最后同步时间、变更拒绝原因、支持手动重试

**文件**: `wms-web/src/views/mes/OpPlanList.vue`

**步骤1**: 在表格中新增3列

在 `<el-table>` 中添加:

```vue
<!-- 在现有列之后添加 -->
<el-table-column prop="syncStatus" label="同步状态" width="110">
  <template #default="{ row }">
    <el-tag v-if="row.syncStatus === 'SYNCED'" type="success" size="small">已同步</el-tag>
    <el-tag v-else-if="row.syncStatus === 'SYNCING'" type="warning" size="small">同步中</el-tag>
    <el-tag v-else-if="row.syncStatus === 'FAILED'" type="danger" size="small">同步失败</el-tag>
    <el-tag v-else type="info" size="small">未同步</el-tag>
  </template>
</el-table-column>

<el-table-column prop="lastSyncTime" label="最后同步时间" width="160" />

<el-table-column prop="changeRejectReason" label="变更拒绝原因" min-width="180" show-overflow-tooltip>
  <template #default="{ row }">
    <span v-if="row.changeRejectReason" class="text-danger">{{ row.changeRejectReason }}</span>
    <span v-else class="text-muted">-</span>
  </template>
</el-table-column>
```

**步骤2**: 添加"手动重试"操作列

在操作列中添加:

```vue
<el-table-column label="操作" width="150" fixed="right">
  <template #default="{ row }">
    <el-button link type="primary" @click="showDetail(row)">详情</el-button>
    <el-button 
      v-if="row.syncStatus === 'FAILED'" 
      link 
      type="warning" 
      @click="handleRetrySync(row)"
    >
      重试同步
    </el-button>
  </template>
</el-table-column>
```

**步骤3**: 实现重试同步方法

在 `<script setup>` 中添加:

```typescript
import { ElMessage, ElMessageBox } from 'element-plus'
import { retryMesOpPlanSync } from '@/api/mes' // 需新增API方法

async function handleRetrySync(row: MesOpPlan) {
  await ElMessageBox.confirm(`确定重新同步工序计划 ${row.planKey}?`, '提示')
  await retryMesOpPlanSync(row.id)
  ElMessage.success('已加入同步队列')
  loadData()
}
```

**步骤4**: 新增后端API

**文件**: `wms-backend/src/main/java/com/wms/mes/controller/MesOpPlanController.java`

```java
@Operation(summary = "手动重试工序计划同步")
@PutMapping("/{id}/retry-sync")
public ApiResult<Void> retrySync(@PathVariable Long id) {
    mesOpPlanService.retrySync(id);
    return ApiResult.ok("已加入同步队列", null);
}
```

**文件**: `wms-backend/src/main/java/com/wms/mes/service/MesOpPlanService.java`

```java
@Transactional
public void retrySync(Long id) {
    MesOpPlan plan = getById(id);
    if (plan == null) {
        throw new BusinessException("工序计划不存在");
    }
    // 重置同步状态
    plan.setSyncStatus(MesConstants.SYNC_PENDING);
    plan.setFailReason(null);
    plan.setLastSyncTime(null);
    updateById(plan);
    log.info("工序计划 {} 已重置为待同步状态", plan.getPlanKey());
}
```

**步骤5**: 前端API方法

**文件**: `wms-web/src/api/mes.ts`

```typescript
export function retryMesOpPlanSync(id: number) {
  return request.put(`/mes/op-plans/${id}/retry-sync`)
}
```

### 任务2.2: 基础资料同步状态 (3小时)

**目标**: 工序/设备/工艺路线列表显示同步状态,支持手动刷新

**涉及文件**: 
- `wms-web/src/views/mes/ProcessList.vue`
- `wms-web/src/views/mes/EquipmentList.vue`
- `wms-web/src/views/mes/RouteList.vue`

**步骤1**: 修改工序列表 (以ProcessList为例,其他类似)

**文件**: `wms-web/src/views/mes/ProcessList.vue`

在查询表单中添加"刷新"按钮:

```vue
<el-form :inline="true">
  <el-form-item label="工序编码">
    <el-input v-model="query.processCode" clearable />
  </el-form-item>
  <el-form-item>
    <el-button type="primary" @click="loadData">查询</el-button>
    <el-button @click="handleAdd">新建</el-button>
    <!-- 新增以下行 -->
    <el-button :loading="syncing" @click="handleRefreshSync">
      刷新同步
    </el-button>
  </el-form-item>
</el-form>
```

在表格中添加同步状态列:

```vue
<el-table-column prop="syncStatus" label="同步状态" width="100">
  <template #default="{ row }">
    <el-tag v-if="row.syncStatus === 'SYNCED'" type="success" size="small">已同步</el-tag>
    <el-tag v-else-if="row.syncStatus === 'SYNCING'" type="warning" size="small">同步中</el-tag>
    <el-tag v-else-if="row.syncStatus === 'FAILED'" type="danger" size="small">失败</el-tag>
    <el-tag v-else type="info" size="small">未同步</el-tag>
  </template>
</el-table-column>

<el-table-column prop="lastSyncTime" label="最后同步" width="160" />
```

在 `<script setup>` 中添加:

```typescript
import { triggerMasterDataSync } from '@/api/mes'

const syncing = ref(false)

async function handleRefreshSync() {
  syncing.value = true
  try {
    await triggerMasterDataSync('process')
    ElMessage.success('同步已触发,请稍后刷新查看结果')
    setTimeout(loadData, 3000) // 3秒后自动刷新列表
  } finally {
    syncing.value = false
  }
}
```

**步骤2**: 后端API

**文件**: `wms-backend/src/main/java/com/wms/mes/controller/MesMasterDataController.java`

```java
@Operation(summary = "手动触发主数据同步")
@PostMapping("/trigger-sync")
public ApiResult<Void> triggerSync(@RequestParam String dataType) {
    // dataType: process / equipment / route
    mesMasterDataService.triggerSync(dataType);
    return ApiResult.ok("同步任务已触发", null);
}
```

**文件**: `wms-backend/src/main/java/com/wms/mes/service/MesMasterDataService.java`

```java
public void triggerSync(String dataType) {
    switch (dataType) {
        case "process":
            syncProcesses();
            break;
        case "equipment":
            syncEquipment();
            break;
        case "route":
            syncRoutes();
            break;
        default:
            throw new BusinessException("不支持的数据类型: " + dataType);
    }
}

// 同步工序 (示例)
@Transactional
public void syncProcesses() {
    try {
        List<MesProcess> processes = mesKingdeePullService.pullProcesses();
        for (MesProcess process : processes) {
            process.setSyncStatus(MesConstants.SYNC_SYNCING);
            process.setLastSyncTime(LocalDateTime.now());
            saveOrUpdate(process);
        }
        // 标记成功
        processes.forEach(p -> {
            p.setSyncStatus(MesConstants.SYNC_SUCCESS);
            updateById(p);
        });
        log.info("工序同步完成,共 {} 条", processes.size());
    } catch (Exception e) {
        log.error("工序同步失败", e);
        // 标记失败
        // ...
    }
}
```

**步骤3**: 前端API方法

**文件**: `wms-web/src/api/mes.ts`

```typescript
export function triggerMasterDataSync(dataType: 'process' | 'equipment' | 'route') {
  return request.post('/mes/master-data/trigger-sync', null, { params: { dataType } })
}
```

### 任务2.3: 报工界面工序类型选择 (4小时)

**目标**: 报工时区分"正常工序报工"和"返工报工"

**文件**: `wms-web/src/views/mes/ReportSubmit.vue`

**步骤1**: 在表单中添加工序类型选择

在工序选择之前插入:

```vue
<el-form :model="form" label-width="100px">
  <el-form-item label="工单号" required>
    <el-input v-model="form.moNo" @blur="loadContext" />
  </el-form-item>
  
  <!-- 新增工序类型选择 -->
  <el-form-item label="工序类型" required>
    <el-radio-group v-model="form.reportType" @change="onReportTypeChange">
      <el-radio 
        label="NORMAL" 
        :disabled="!canReportNormal"
      >
        正常工序报工
      </el-radio>
      <el-radio 
        label="REWORK" 
        :disabled="!canReportRework"
      >
        返工报工
      </el-radio>
    </el-radio-group>
    <div v-if="!canReportNormal && form.reportType === 'NORMAL'" class="text-muted">
      提示: 正常工序已完成,仅可进行返工报工
    </div>
  </el-form-item>
  
  <el-form-item label="工序" required>
    <WmsSelect v-model="form.processCode" filterable>
      <el-option
        v-for="p in availablePlans"
        :key="p.processCode"
        :label="`${p.processName} (已报${p.reportedQty}/${p.planQty})`"
        :value="p.processCode"
      />
    </WmsSelect>
  </el-form-item>
  
  <!-- 返工时显示关联不良单 -->
  <el-form-item v-if="form.reportType === 'REWORK'" label="关联不良单">
    <WmsSelect v-model="form.defectNo" filterable>
      <el-option
        v-for="d in availableDefects"
        :key="d.defectNo"
        :label="`${d.defectNo} (${d.defectType})`"
        :value="d.defectNo"
      />
    </WmsSelect>
  </el-form-item>
  
  <!-- 其他字段... -->
</el-form>
```

**步骤2**: 逻辑实现

```typescript
import { computed, reactive, ref, watch } from 'vue'
import type { MesOpPlan, MesDefect } from '@/api/mes'

const form = reactive({
  moNo: '',
  reportType: 'NORMAL', // NORMAL / REWORK
  processCode: '',
  defectNo: '',
  qty: 1,
  // ...
})

const plans = ref<MesOpPlan[]>([])
const defects = ref<MesDefect[]>([])

// 是否可以正常报工
const canReportNormal = computed(() => {
  const plan = plans.value.find(p => p.processCode === form.processCode)
  if (!plan) return true
  // 已报工数量 < 计划数量 -> 可以正常报工
  return plan.reportedQty < plan.planQty
})

// 是否可以返工报工
const canReportRework = computed(() => {
  // 只要有不良单就可以返工
  return defects.value.length > 0
})

// 根据工序类型过滤可选工序
const availablePlans = computed(() => {
  if (form.reportType === 'NORMAL') {
    // 正常报工: 显示未完成的工序
    return plans.value.filter(p => p.reportedQty < p.planQty)
  } else {
    // 返工报工: 显示有返工序列的工序
    return plans.value.filter(p => hasReworkOp(p.processCode))
  }
})

const availableDefects = computed(() => {
  // 过滤当前工序相关的不良单
  return defects.value.filter(d => 
    d.reworkProcessCodes?.includes(form.processCode)
  )
})

function hasReworkOp(processCode: string): boolean {
  return defects.value.some(d => 
    d.reworkProcessCodes?.includes(processCode)
  )
}

async function loadContext() {
  if (!form.moNo.trim()) return
  const ctx = await getMesReportContext(form.moNo.trim())
  plans.value = ctx.plans || []
  defects.value = ctx.defects || []
  
  // 自动选择工序类型
  if (plans.value.some(p => p.reportedQty < p.planQty)) {
    form.reportType = 'NORMAL'
  } else if (defects.value.length > 0) {
    form.reportType = 'REWORK'
  }
}

function onReportTypeChange() {
  // 切换类型时清空工序选择
  form.processCode = ''
  form.defectNo = ''
}
```

**步骤3**: 后端接口增强

**文件**: `wms-backend/src/main/java/com/wms/mes/dto/MesReportContextVo.java`

确保包含:

```java
@Data
public class MesReportContextVo {
    private List<MesOpPlan> plans;
    private List<MesDefect> defects; // 新增: 不良单列表
    private Map<String, BigDecimal> overReceiveRatios;
}
```

**文件**: `wms-backend/src/main/java/com/wms/mes/service/MesReportService.java`

```java
public MesReportContextVo getReportContext(String moNo) {
    MesReportContextVo vo = new MesReportContextVo();
    
    // 查询工序计划
    List<MesOpPlan> plans = opPlanService.listByMoNo(moNo);
    vo.setPlans(plans);
    
    // 查询不良单 (新增)
    List<MesDefect> defects = defectMapper.selectList(
        new LambdaQueryWrapper<MesDefect>()
            .eq(MesDefect::getMoNo, moNo)
            .in(MesDefect::getReworkStatus, "IN_PROGRESS", "PENDING")
    );
    vo.setDefects(defects);
    
    // 查询超收比例
    // ...
    
    return vo;
}
```

### 任务2.4: 同步监控增强 (4小时)

**目标**: 暂存数据生命周期管理、队列满告警、5分钟SLA监控

**步骤1**: 创建数据清理定时任务

**文件**: `wms-backend/src/main/java/com/wms/mes/schedule/MesDataCleanupTask.java`

```java
package com.wms.mes.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.mes.MesConstants;
import com.wms.mes.entity.MesReport;
import com.wms.mes.entity.MesTransfer;
import com.wms.mes.mapper.MesReportMapper;
import com.wms.mes.mapper.MesTransferMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class MesDataCleanupTask {

    private final MesReportMapper reportMapper;
    private final MesTransferMapper transferMapper;

    /**
     * 每天凌晨2点清理过期数据
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredData() {
        log.info("开始清理MES过期数据");
        
        LocalDateTime now = LocalDateTime.now();
        
        // 待同步数据超过30天 -> 标记为长期未同步
        LocalDateTime pendingExpiry = now.minusDays(30);
        int pendingCount = reportMapper.update(null, 
            new LambdaQueryWrapper<MesReport>()
                .eq(MesReport::getSyncStatus, MesConstants.SYNC_PENDING)
                .lt(MesReport::getCreateTime, pendingExpiry)
                .set(MesReport::getSyncStatus, "STALE")
                .set(MesReport::getFailReason, "长期未同步,已标记为过期")
        );
        
        // 同步失败数据超过30天 -> 标记为超期未处理
        int failedCount = reportMapper.update(null,
            new LambdaQueryWrapper<MesReport>()
                .eq(MesReport::getSyncStatus, MesConstants.SYNC_FAILED)
                .lt(MesReport::getCreateTime, pendingExpiry)
                .set(MesReport::getSyncStatus, "EXPIRED")
                .set(MesReport::getFailReason, "超期未处理")
        );
        
        // 已同步数据超过90天 -> 可选择归档或删除
        LocalDateTime syncedExpiry = now.minusDays(90);
        int syncedCount = reportMapper.selectCount(
            new LambdaQueryWrapper<MesReport>()
                .eq(MesReport::getSyncStatus, MesConstants.SYNC_SUCCESS)
                .lt(MesReport::getSyncTime, syncedExpiry)
        );
        // 这里仅统计,不删除,由管理员决定是否清理
        
        log.info("MES数据清理完成: 标记过期{}条, 标记超期{}条, 可归档{}条", 
            pendingCount, failedCount, syncedCount);
    }
}
```

**步骤2**: 队列满告警

**文件**: `wms-backend/src/main/java/com/wms/mes/service/MesSyncWorkerService.java`

在 `drainQueue()` 方法开头添加:

```java
public void drainQueue() {
    // 检查队列长度
    long queueSize = countReport(MesConstants.SYNC_PENDING) + countTransfer(MesConstants.SYNC_PENDING);
    if (queueSize > 10000) {
        log.error("MES同步队列已满: {} 条记录待同步", queueSize);
        // 发送告警 (可集成钉钉/邮件/短信)
        sendAlert("MES同步队列已满", String.format("当前队列长度: %d", queueSize));
        return; // 暂停接收新数据
    }
    
    if (!kingdeeCloudService.isEnabled() || !healthService.isOnline()) {
        log.debug("MES sync skip: ERP unavailable");
        return;
    }
    processReports();
    processTransfers();
    markStale();
}

private void sendAlert(String title, String content) {
    // TODO: 集成告警通道
    log.warn("告警: {} - {}", title, content);
}
```

**步骤3**: 5分钟SLA监控

**文件**: `wms-backend/src/main/java/com/wms/mes/schedule/MesSyncMonitorTask.java`

```java
package com.wms.mes.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.mes.MesConstants;
import com.wms.mes.entity.MesReport;
import com.wms.mes.mapper.MesReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MesSyncMonitorTask {

    private final MesReportMapper reportMapper;

    /**
     * 每分钟检查一次5分钟SLA
     */
    @Scheduled(fixedRate = 60000)
    public void monitorSyncSla() {
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        
        // 查询超过5分钟还未同步的记录
        List<MesReport> overdue = reportMapper.selectList(
            new LambdaQueryWrapper<MesReport>()
                .in(MesReport::getSyncStatus, MesConstants.SYNC_PENDING, MesConstants.SYNC_SYNCING)
                .lt(MesReport::getReportTime, fiveMinutesAgo)
        );
        
        if (!overdue.isEmpty()) {
            log.warn("发现{}条报工记录超过5分钟SLA未同步", overdue.size());
            for (MesReport report : overdue) {
                log.warn("超时报工: reportNo={}, moNo={}, reportTime={}", 
                    report.getReportNo(), report.getMoNo(), report.getReportTime());
            }
            // 可以在同步中心面板高亮显示这些记录
        }
    }
}
```

### 任务2.5: 返工序列自动创建优化 (5小时)

**目标**: 实现"返工汇合工序"标识,完善返工序列生成逻辑

**步骤1**: 数据库增加字段

**文件**: SQL迁移脚本

```sql
-- 在工序表增加"是否汇合工序"标识
ALTER TABLE mes_process ADD COLUMN is_converge_op TINYINT(1) DEFAULT 0 COMMENT '是否返工汇合工序';

-- 在工艺路线工序表增加"是否汇合工序"标识
ALTER TABLE mes_route_op ADD COLUMN is_converge_op TINYINT(1) DEFAULT 0 COMMENT '是否返工汇合工序';
```

**步骤2**: 实体类添加字段

**文件**: `wms-backend/src/main/java/com/wms/mes/entity/MesProcess.java`

```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_process")
public class MesProcess extends BaseEntity {
    // ... 现有字段
    
    private Boolean isConvergeOp; // 新增: 是否返工汇合工序
}
```

**文件**: `wms-backend/src/main/java/com/wms/mes/entity/MesRouteOp.java`

```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_route_op")
public class MesRouteOp extends BaseEntity {
    // ... 现有字段
    
    private Boolean isConvergeOp; // 新增: 是否返工汇合工序
}
```

**步骤3**: 返工序列生成逻辑

**文件**: `wms-backend/src/main/java/com/wms/mes/service/MesReworkService.java`

```java
/**
 * 自动创建返工工序序列
 */
@Transactional
public List<MesReworkOp> createReworkSequence(String defectNo, String sourceProcessCode, BigDecimal defectQty) {
    MesDefect defect = defectMapper.selectOne(
        new LambdaQueryWrapper<MesDefect>()
            .eq(MesDefect::getDefectNo, defectNo)
    );
    if (defect == null) {
        throw new BusinessException("不良单不存在");
    }
    
    // 查询工艺路线
    List<MesRouteOp> routeOps = routeOpMapper.selectList(
        new LambdaQueryWrapper<MesRouteOp>()
            .eq(MesRouteOp::getProductCode, defect.getProductCode())
            .orderByAsc(MesRouteOp::getSeqNo)
    );
    
    // 找到不良工序位置
    int defectIdx = -1;
    for (int i = 0; i < routeOps.size(); i++) {
        if (routeOps.get(i).getProcessCode().equals(sourceProcessCode)) {
            defectIdx = i;
            break;
        }
    }
    
    if (defectIdx == -1) {
        throw new BusinessException("不良工序在工艺路线中不存在");
    }
    
    // 向上查找最近的返工汇合工序
    int convergeIdx = -1;
    for (int i = defectIdx + 1; i < routeOps.size(); i++) {
        if (Boolean.TRUE.equals(routeOps.get(i).getIsConvergeOp())) {
            convergeIdx = i;
            break;
        }
    }
    
    // 如果没有汇合工序,默认到最后一个工序
    if (convergeIdx == -1) {
        convergeIdx = routeOps.size() - 1;
    }
    
    // 生成返工序列: 不良工序 -> 汇合工序
    List<MesReworkOp> reworkOps = new ArrayList<>();
    for (int i = defectIdx; i <= convergeIdx; i++) {
        MesRouteOp routeOp = routeOps.get(i);
        MesReworkOp reworkOp = new MesReworkOp();
        reworkOp.setDefectNo(defectNo);
        reworkOp.setSeqNo(i - defectIdx + 1);
        reworkOp.setProcessCode(routeOp.getProcessCode());
        reworkOp.setProcessName(routeOp.getProcessName());
        reworkOp.setPlanQty(defectQty);
        reworkOp.setReportedQty(BigDecimal.ZERO);
        reworkOp.setOpStatus("PENDING");
        reworkOpMapper.insert(reworkOp);
        reworkOps.add(reworkOp);
    }
    
    log.info("为不良单 {} 创建返工序列,共 {} 个工序", defectNo, reworkOps.size());
    return reworkOps;
}
```

**步骤4**: 前端工艺路线维护

**文件**: `wms-web/src/views/mes/RouteList.vue`

在工艺路线详情编辑时,增加"是否汇合工序"复选框:

```vue
<el-form-item label="是否汇合工序">
  <el-checkbox v-model="opForm.isConvergeOp">
    返工汇合点 (返工序列在此工序结束)
  </el-checkbox>
</el-form-item>
```

---

## 阶段3: WMS缺失模块补全 (2-4周)

### 任务3.1: 质检管理模块 (1周)

**目标**: 实现质检标准、来料质检、质检报告、让步接收流程

#### 子任务3.1.1: 后端质检控制器

**文件**: `wms-backend/src/main/java/com/wms/quality/controller/QualityController.java`

```java
package com.wms.quality.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.quality.dto.*;
import com.wms.quality.entity.QcOrder;
import com.wms.quality.entity.QcStandard;
import com.wms.quality.service.QualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "质检管理")
@RestController
@RequestMapping("/quality")
@RequiredArgsConstructor
public class QualityController {

    private final QualityService qualityService;

    @Operation(summary = "质检标准列表")
    @GetMapping("/standards")
    public ApiResult<PageResult<QcStandard>> listStandards(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String qcType,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(qualityService.pageStandards(materialCode, qcType, current, size));
    }

    @Operation(summary = "创建质检标准")
    @PostMapping("/standards")
    public ApiResult<Void> createStandard(@RequestBody QcStandardCreateRequest request) {
        qualityService.createStandard(request);
        return ApiResult.ok("创建成功", null);
    }

    @Operation(summary = "质检单列表")
    @GetMapping("/orders")
    public ApiResult<PageResult<QcOrder>> listOrders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(qualityService.pageOrders(orderNo, materialCode, status, current, size));
    }

    @Operation(summary = "创建质检单")
    @PostMapping("/orders")
    public ApiResult<String> createOrder(@RequestBody QcOrderCreateRequest request) {
        return ApiResult.ok("创建成功", qualityService.createOrder(request));
    }

    @Operation(summary = "质检判定")
    @PutMapping("/orders/{orderNo}/judge")
    public ApiResult<Void> judgeOrder(
            @PathVariable String orderNo,
            @RequestBody QcJudgeRequest request) {
        qualityService.judgeOrder(orderNo, request);
        return ApiResult.ok("判定成功", null);
    }

    @Operation(summary = "让步接收")
    @PutMapping("/orders/{orderNo}/concession")
    public ApiResult<Void> concessionAccept(
            @PathVariable String orderNo,
            @RequestBody QcConcessionRequest request) {
        qualityService.concessionAccept(orderNo, request);
        return ApiResult.ok("让步接收成功", null);
    }
}
```

#### 子任务3.1.2: 质检服务层

**文件**: `wms-backend/src/main/java/com/wms/quality/service/QualityService.java`

```java
package com.wms.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.quality.dto.*;
import com.wms.quality.entity.QcOrder;
import com.wms.quality.entity.QcStandard;
import com.wms.quality.mapper.QcOrderMapper;
import com.wms.quality.mapper.QcStandardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualityService {

    private final QcStandardMapper standardMapper;
    private final QcOrderMapper orderMapper;

    public PageResult<QcStandard> pageStandards(String materialCode, String qcType, long current, long size) {
        Page<QcStandard> page = standardMapper.selectPage(
            new Page<>(current, size),
            new LambdaQueryWrapper<QcStandard>()
                .like(StringUtils.hasText(materialCode), QcStandard::getMaterialCode, materialCode)
                .eq(StringUtils.hasText(qcType), QcStandard::getQcType, qcType)
                .orderByDesc(QcStandard::getCreateTime)
        );
        return PageResult.of(page);
    }

    @Transactional
    public void createStandard(QcStandardCreateRequest request) {
        QcStandard standard = new QcStandard();
        standard.setMaterialCode(request.getMaterialCode());
        standard.setMaterialName(request.getMaterialName());
        standard.setQcType(request.getQcType());
        standard.setQcItems(request.getQcItems());
        standard.setStatus("ACTIVE");
        standardMapper.insert(standard);
    }

    public PageResult<QcOrder> pageOrders(String orderNo, String materialCode, String status, long current, long size) {
        Page<QcOrder> page = orderMapper.selectPage(
            new Page<>(current, size),
            new LambdaQueryWrapper<QcOrder>()
                .like(StringUtils.hasText(orderNo), QcOrder::getOrderNo, orderNo)
                .like(StringUtils.hasText(materialCode), QcOrder::getMaterialCode, materialCode)
                .eq(StringUtils.hasText(status), QcOrder::getStatus, status)
                .orderByDesc(QcOrder::getCreateTime)
        );
        return PageResult.of(page);
    }

    @Transactional
    public String createOrder(QcOrderCreateRequest request) {
        QcOrder order = new QcOrder();
        order.setOrderNo("QC" + System.currentTimeMillis());
        order.setSourceType(request.getSourceType());
        order.setSourceNo(request.getSourceNo());
        order.setMaterialCode(request.getMaterialCode());
        order.setMaterialName(request.getMaterialName());
        order.setBatchNo(request.getBatchNo());
        order.setQcQty(request.getQcQty());
        order.setQcType(request.getQcType());
        order.setStatus("PENDING");
        orderMapper.insert(order);
        return order.getOrderNo();
    }

    @Transactional
    public void judgeOrder(String orderNo, QcJudgeRequest request) {
        QcOrder order = orderMapper.selectOne(
            new LambdaQueryWrapper<QcOrder>()
                .eq(QcOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            throw new BusinessException("质检单不存在");
        }
        
        order.setQualifiedQty(request.getQualifiedQty());
        order.setUnqualifiedQty(request.getUnqualifiedQty());
        order.setJudgeResult(request.getJudgeResult()); // QUALIFIED / UNQUALIFIED / CONCESSION
        order.setJudgeRemark(request.getJudgeRemark());
        order.setJudgeTime(LocalDateTime.now());
        order.setJudgeBy(request.getJudgeBy());
        
        if ("QUALIFIED".equals(request.getJudgeResult())) {
            order.setStatus("COMPLETED");
        } else if ("UNQUALIFIED".equals(request.getJudgeResult())) {
            order.setStatus("REJECTED");
        } else if ("CONCESSION".equals(request.getJudgeResult())) {
            order.setStatus("CONCESSION_PENDING");
        }
        
        orderMapper.updateById(order);
        log.info("质检单 {} 判定完成: {}", orderNo, request.getJudgeResult());
    }

    @Transactional
    public void concessionAccept(String orderNo, QcConcessionRequest request) {
        QcOrder order = orderMapper.selectOne(
            new LambdaQueryWrapper<QcOrder>()
                .eq(QcOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            throw new BusinessException("质检单不存在");
        }
        if (!"CONCESSION_PENDING".equals(order.getStatus())) {
            throw new BusinessException("质检单状态不允许让步接收");
        }
        
        order.setConcessionReason(request.getConcessionReason());
        order.setConcessionApprover(request.getApprover());
        order.setConcessionTime(LocalDateTime.now());
        order.setStatus("CONCESSION_ACCEPTED");
        
        orderMapper.updateById(order);
        log.info("质检单 {} 让步接收完成", orderNo);
    }
}
```

#### 子任务3.1.3: Web端质检标准页面

**文件**: `wms-web/src/views/quality/QcStandardList.vue`

```vue
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getQcStandards, createQcStandard, type QcStandard } from '@/api/quality'

const loading = ref(false)
const tableData = ref<QcStandard[]>([])
const total = ref(0)
const query = reactive({ materialCode: '', qcType: '', current: 1, size: 20 })

const dialogVisible = ref(false)
const form = reactive({
  materialCode: '',
  materialName: '',
  qcType: 'INCOMING',
  qcItems: '',
})

async function loadData() {
  loading.value = true
  try {
    const res = await getQcStandards(query)
    tableData.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  Object.assign(form, {
    materialCode: '',
    materialName: '',
    qcType: 'INCOMING',
    qcItems: '',
  })
  dialogVisible.value = true
}

async function handleCreate() {
  await createQcStandard(form)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  loadData()
}

onMounted(loadData)
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true">
      <el-form-item label="物料编码">
        <el-input v-model="query.materialCode" clearable />
      </el-form-item>
      <el-form-item label="质检类型">
        <WmsSelect v-model="query.qcType" clearable>
          <el-option label="来料质检" value="INCOMING" />
          <el-option label="过程质检" value="PROCESS" />
          <el-option label="成品质检" value="FINAL" />
        </WmsSelect>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="handleAdd">新建标准</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="materialCode" label="物料编码" width="120" />
      <el-table-column prop="materialName" label="物料名称" min-width="160" />
      <el-table-column prop="qcType" label="质检类型" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.qcType === 'INCOMING'" type="primary">来料</el-tag>
          <el-tag v-else-if="row.qcType === 'PROCESS'" type="warning">过程</el-tag>
          <el-tag v-else type="success">成品</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="qcItems" label="检验项目" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'ACTIVE'" type="success" size="small">启用</el-tag>
          <el-tag v-else type="info" size="small">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary">编辑</el-button>
          <el-button link type="danger">停用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.current"
      v-model:page-size="query.size"
      :total="total"
      layout="total, sizes, prev, pager, next"
      @current-change="loadData"
      @size-change="loadData"
    />
  </el-card>

  <el-dialog v-model="dialogVisible" title="新建质检标准" width="600px">
    <el-form :model="form" label-width="100px">
      <el-form-item label="物料编码" required>
        <el-input v-model="form.materialCode" />
      </el-form-item>
      <el-form-item label="物料名称" required>
        <el-input v-model="form.materialName" />
      </el-form-item>
      <el-form-item label="质检类型" required>
        <WmsSelect v-model="form.qcType">
          <el-option label="来料质检" value="INCOMING" />
          <el-option label="过程质检" value="PROCESS" />
          <el-option label="成品质检" value="FINAL" />
        </WmsSelect>
      </el-form-item>
      <el-form-item label="检验项目" required>
        <el-input v-model="form.qcItems" type="textarea" :rows="4" 
          placeholder="每行一个检验项目,格式: 项目名称|标准值|公差" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleCreate">确定</el-button>
    </template>
  </el-dialog>
</template>
```

#### 子任务3.1.4: Web端质检单页面

**文件**: `wms-web/src/views/quality/QcOrderList.vue`

(结构类似QcStandardList,增加判定/让步接收操作按钮)

#### 子任务3.1.5: 路由和菜单配置

**文件**: `wms-web/src/router/index.ts`

```typescript
{
  path: 'quality/standards',
  name: 'QualityStandards',
  component: () => import('@/views/quality/QcStandardList.vue'),
  meta: { title: '质检标准', permission: MENU_PERMISSIONS['/quality/standards'] },
},
{
  path: 'quality/orders',
  name: 'QualityOrders',
  component: () => import('@/views/quality/QcOrderList.vue'),
  meta: { title: '质检单', permission: MENU_PERMISSIONS['/quality/orders'] },
},
```

**文件**: `wms-web/src/layouts/MainLayout.vue`

```typescript
{
  index: 'quality',
  title: '质检管理',
  icon: Document,
  children: [
    { path: '/quality/standards', title: '质检标准', permission: MENU_PERMISSIONS['/quality/standards'] },
    { path: '/quality/orders', title: '质检单', permission: MENU_PERMISSIONS['/quality/orders'] },
  ],
},
```

### 任务3.2: 其他快速补全项 (简化说明)

#### 3.2.1 盘点任务列表页面 (2天)

复制 `PlanList.vue` 结构,调用 `/stockcheck/tasks` 接口

#### 3.2.2 盘点差异列表页面 (2天)

复制 `PlanList.vue` 结构,调用 `/stockcheck/diffs` 接口,增加审批按钮

#### 3.2.3 库存流水查询页面 (2天)

**文件**: `wms-web/src/views/inventory/TransactionList.vue`

调用后端 `/inventory/transactions` 接口(已有)

#### 3.2.4 基础数据模块 (1周)

参考现有 `MaterialList.vue/WarehouseList.vue` 结构,实现:
- 物料分类 (CategoryList.vue)
- 客户管理 (CustomerList.vue)
- 计量单位 (UnitList.vue)
- 字典管理 (DictList.vue)

---

## 总结与交付时间表

### 交付计划

| 阶段 | 任务 | 预计工时 | 交付物 |
|------|------|----------|--------|
| 阶段1 | WMS核心功能接线 | 4小时 | 出库单/移库/盘点计划可用 |
| 阶段2.1 | 工序计划列表增强 | 4小时 | 同步状态/重试功能 |
| 阶段2.2 | 基础资料同步状态 | 3小时 | 刷新按钮/同步状态 |
| 阶段2.3 | 报工界面优化 | 4小时 | 正常/返工类型选择 |
| 阶段2.4 | 同步监控增强 | 4小时 | 数据清理/队列告警/SLA监控 |
| 阶段2.5 | 返工序列优化 | 5小时 | 汇合工序/自动生成 |
| 阶段3.1 | 质检管理模块 | 5天 | 质检标准/质检单/让步接收 |
| 阶段3.2 | 其他模块补全 | 2周 | 盘点任务/库存流水/基础数据 |

### 总工时

- 阶段1: 0.5天 (立即见效)
- 阶段2: 2.5天 (体验优化)
- 阶段3: 3周 (功能完整)

**总计**: 约4周完整交付

### 关键路径

1. 第1天上午: 完成阶段1,3个核心功能立即上线
2. 第1-3天: 完成阶段2,MES用户体验显著提升
3. 第4-25天: 逐步补全WMS缺失模块

### 风险应对

1. **电子秤对接**: 如硬件不支持,改为手动输入重量
2. **权限配置**: 提前准备SQL脚本或配置文档
3. **ERP兼容性**: 与ERP团队提前确认接口契约

### 验收标准

每个阶段完成后,按照对应的"验收测试"章节执行测试用例,确保功能正常后再进入下一阶段。
