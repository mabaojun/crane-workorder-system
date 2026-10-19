<template>
  <div v-loading="loading">
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="detail-head">
          <span>工单 {{ order?.orderNo || '' }}</span>
          <status-tag v-if="order" kind="workorder" :status="order.status" />
        </div>
      </template>

      <!-- 进度：待接单 → 待到场 → 进行中 → 完成 -->
      <el-steps v-if="order" :active="stepActive" align-center class="steps">
        <el-step title="待接单" />
        <el-step title="待到场" />
        <el-step title="进行中" />
        <el-step title="完成" />
      </el-steps>
      <div v-if="order && ['CANCELLED', 'SUSPENDED'].includes(order.status)" class="abnormal">
        <el-alert
          :title="order.status === 'CANCELLED' ? `工单已取消：${order.cancelledReason || '无原因'}` : '工单施工受阻（暂停中）'"
          :type="order.status === 'CANCELLED' ? 'error' : 'warning'"
          :closable="false"
          show-icon
        />
      </div>

      <!-- 操作区 -->
      <div v-if="order" class="actions">
        <el-button
          v-if="order.status === 'PENDING_ACCEPT'"
          type="warning"
          @click="openDispatch"
        >派单 / 改派</el-button>
        <el-button
          v-if="order.status === 'IN_PROGRESS'"
          type="warning"
          plain
          @click="changeStatus('SUSPEND')"
        >暂停</el-button>
        <el-button
          v-if="order.status === 'SUSPENDED'"
          type="success"
          plain
          @click="changeStatus('RESUME')"
        >恢复</el-button>
        <el-button
          v-if="!['COMPLETED', 'CANCELLED'].includes(order.status)"
          type="danger"
          plain
          @click="changeStatus('CANCEL')"
        >取消工单</el-button>
        <el-button
          v-if="!['COMPLETED', 'CANCELLED'].includes(order.status)"
          type="primary"
          plain
          @click="onSettle"
        >兜底结单</el-button>
        <el-button
          v-if="!['COMPLETED', 'CANCELLED'].includes(order.status)"
          type="primary"
          plain
          @click="openEdit"
        >修改工单</el-button>
        <el-button @click="router.back()">返回</el-button>
      </div>
    </el-card>

    <el-card v-if="order" shadow="never" class="page-card">
      <template #header>基本信息</template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="任务名称">{{ order.taskName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ ORDER_SOURCE_LABEL[order.source] || order.source }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ order.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结算主体">
          <el-tag :type="order.settlementType === 'PERSONAL' ? 'info' : 'primary'" effect="plain" size="small">
            {{ order.settlementType === 'PERSONAL' ? '个人结算' : (order.settlementCustomerName || order.customerName || '企业主体') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="司机">{{ order.staffName || '-' }}{{ order.staffPhone ? `（${order.staffPhone}）` : '' }}</el-descriptions-item>
        <el-descriptions-item label="车辆">{{ order.vehiclePlateNo || '-' }}{{ order.vehicleTonnage ? `｜${order.vehicleTonnage}吨` : '' }}</el-descriptions-item>
        <el-descriptions-item label="计划开始">{{ fmtTime(order.expectedStartAt) }}</el-descriptions-item>
        <el-descriptions-item label="施工地址" :span="2">{{ order.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="经纬度">{{ order.longitude ?? '-' }}, {{ order.latitude ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="货物名称">{{ order.cargoName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="货物数量">{{ order.cargoQuantity ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="货物重量">{{ order.cargoWeight != null ? `${order.cargoWeight} 吨` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="吊车吨位">{{ order.craneTonnage != null ? `${order.craneTonnage} 吨` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="接单时间">{{ fmtTime(order.acceptedAt) }}</el-descriptions-item>
        <el-descriptions-item label="开工时间">{{ fmtTime(order.startedAt) }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ fmtTime(order.completedAt) }}</el-descriptions-item>
        <el-descriptions-item label="客户可见状态">{{ order.customerVisibleStatusLabel || order.customerVisibleStatus || '-' }}</el-descriptions-item>
        <el-descriptions-item label="接单截止">{{ fmtTime(order.acceptDeadlineAt) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ order.remark || '无' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="page-card">
      <template #header>状态变更记录</template>
      <el-timeline v-if="logs.length">
        <el-timeline-item
          v-for="log in logs"
          :key="log.id"
          :timestamp="fmtTime(log.createdAt)"
          placement="top"
        >
          <span class="log-line">
            {{ statusLabel(log.fromStatus) }} → {{ statusLabel(log.toStatus) }}
          </span>
          <span v-if="log.remark" class="log-remark">{{ log.remark }}</span>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无状态记录" :image-size="60" />
    </el-card>

    <!-- 受阻工单：异常记录与批复 -->
    <el-card v-if="order && order.status === 'SUSPENDED'" shadow="never" class="page-card">
      <template #header>异常记录</template>
      <el-table v-loading="exceptionLoading" :data="exceptions" border stripe>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ exceptionTypeName(row) }}</template>
        </el-table-column>
        <el-table-column label="描述" prop="description" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <status-tag kind="exception" :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="上报时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.reportedAt) }}</template>
        </el-table-column>
        <el-table-column label="批复指令" width="110">
          <template #default="{ row }">{{ row.instruction ? INSTRUCTION_LABEL[row.instruction] || row.instruction : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              size="small"
              text
              type="primary"
              @click="openReply(row)"
            >批复</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 派单 / 改派弹窗 -->
    <dispatch-dialog
      v-if="order"
      v-model="dispatchVisible"
      mode="dispatch"
      title="派单 / 改派"
      :prefill="{ planStartAt: order.expectedStartAt || undefined }"
      :submit="submitDispatch"
    />

    <!-- 修改工单弹窗 -->
    <work-order-edit-dialog
      v-if="order"
      v-model:visible="editVisible"
      :order="order"
      @updated="loadAll"
    />

    <!-- 异常批复弹窗 -->
    <exception-reply-dialog
      v-model="replyVisible"
      :record="replyTarget"
      :submit="submitReply"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WORK_ORDER_STATUS_LABEL } from '@crane/shared'
import {
  getWorkOrder,
  changeWorkOrderStatus,
  settleWorkOrder,
  getWorkOrderStatusLogs,
  type WorkOrder,
  type WorkOrderStatusLog
} from '@crane/shared/api/workorder'
import { dispatchOrder } from '@crane/shared/api/dispatch'
import {
  pageExceptions,
  replyException,
  type ExceptionRecord
} from '@crane/shared/api/exception'
import { fmtTime } from '../../utils/format'
import { EXCEPTION_TYPE_LABEL, INSTRUCTION_LABEL, ORDER_SOURCE_LABEL } from '../../utils/tags'
import DispatchDialog from '../../components/DispatchDialog.vue'
import WorkOrderEditDialog from '../../components/WorkOrderEditDialog.vue'
import ExceptionReplyDialog from '../../components/ExceptionReplyDialog.vue'
import StatusTag from '../../components/StatusTag.vue'

const route = useRoute()
const router = useRouter()
const orderId = route.params.id as string

const loading = ref(false)
const order = ref<WorkOrder | null>(null)
const logs = ref<WorkOrderStatusLog[]>([])
const exceptions = ref<ExceptionRecord[]>([])
const exceptionLoading = ref(false)

const dispatchVisible = ref(false)
const editVisible = ref(false)
const replyVisible = ref(false)
const replyTarget = ref<ExceptionRecord | null>(null)

const STEP_ORDER = ['PENDING_ACCEPT', 'PENDING_ARRIVE', 'IN_PROGRESS', 'COMPLETED']
const stepActive = computed(() => {
  if (!order.value) return 0
  const idx = STEP_ORDER.indexOf(order.value.status)
  if (idx >= 0) return idx
  return 0
})

onMounted(async () => {
  await loadAll()
})

async function loadAll() {
  loading.value = true
  try {
    order.value = await getWorkOrder(orderId)
    const logPage = await getWorkOrderStatusLogs(orderId)
    logs.value = logPage.list
    if (order.value.status === 'SUSPENDED') await loadExceptions()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '工单详情加载失败')
  } finally {
    loading.value = false
  }
}

async function loadExceptions() {
  exceptionLoading.value = true
  try {
    const page = await pageExceptions({ orderId: Number(orderId), pageNum: 1, pageSize: 50 })
    exceptions.value = page.list
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '异常记录加载失败')
  } finally {
    exceptionLoading.value = false
  }
}

function statusLabel(status?: string | null) {
  if (!status) return '-'
  return WORK_ORDER_STATUS_LABEL[status] || status
}

function exceptionTypeName(row: ExceptionRecord) {
  return row.type === 'CUSTOM'
    ? (row.typeLabel || EXCEPTION_TYPE_LABEL.CUSTOM)
    : (EXCEPTION_TYPE_LABEL[row.type] || row.type)
}

// ==================== 派单 / 改派 ====================
function openDispatch() {
  dispatchVisible.value = true
}

// ==================== 修改工单 ====================
function openEdit() {
  editVisible.value = true
}

async function submitDispatch(payload: {
  staffId: number
  vehicleId: number
  planStartAt: string
  remark: string
  confirmPast: boolean
}) {
  await dispatchOrder(orderId, {
    staffId: payload.staffId,
    vehicleId: payload.vehicleId,
    planStartAt: payload.planStartAt,
    confirmPast: payload.confirmPast,
    remark: payload.remark || undefined
  })
  ElMessage.success('派单成功，等待司机接单')
  loadAll()
}

// ==================== 状态操作 ====================
async function changeStatus(action: 'CANCEL' | 'SUSPEND' | 'RESUME') {
  const actionLabel = { CANCEL: '取消', SUSPEND: '暂停', RESUME: '恢复' }[action]
  try {
    const { value } = await ElMessageBox.prompt(
      `确认${actionLabel}工单 ${order.value?.orderNo}？${action === 'RESUME' ? '' : '请填写原因：'}`,
      `${actionLabel}工单`,
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        inputPlaceholder: action === 'RESUME' ? '原因（可选）' : '原因',
        inputValidator: (v: string) =>
          action === 'RESUME' || (v && v.trim()) ? true : '原因不能为空'
      }
    )
    await changeWorkOrderStatus(orderId, { action, reason: value.trim() || undefined })
    ElMessage.success(`${actionLabel}成功`)
    loadAll()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

async function onSettle() {
  try {
    await ElMessageBox.confirm(
      `兜底结单将直接把工单置为已完成并开放客户评价，确认执行？`,
      '兜底结单确认',
      { type: 'warning', confirmButtonText: '确认结单', cancelButtonText: '再想想' }
    )
  } catch {
    return
  }
  try {
    await settleWorkOrder(orderId)
    ElMessage.success('结单成功')
    loadAll()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '结单失败')
  }
}

// ==================== 异常批复 ====================
function openReply(row: ExceptionRecord) {
  replyTarget.value = row
  replyVisible.value = true
}

async function submitReply(payload: { approve: boolean; instruction?: string; opinion: string }) {
  if (!replyTarget.value) return
  await replyException(replyTarget.value.id, payload)
  ElMessage.success('批复完成')
  loadAll()
}
</script>

<style scoped>
.detail-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.steps { margin: 10px 0 4px; }
.abnormal { margin-top: 14px; }
.actions {
  display: flex;
  gap: 0;
  margin-top: 16px;
}
.log-line { font-weight: 500; }
.log-remark { color: var(--sub); margin-left: 10px; font-size: 13px; }
</style>
