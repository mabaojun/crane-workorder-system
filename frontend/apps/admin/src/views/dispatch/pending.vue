<template>
  <div>
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="head-row">
          <span>司机拒单后的工单会进入本队列，需管理员重新派单。</span>
          <el-form inline class="toolbar-form" @submit.prevent="onSearch">
            <el-form-item>
              <el-input
                v-model="keyword"
                placeholder="工单号 / 任务名"
                clearable
                style="width: 200px"
                @keyup.enter="onSearch"
                @clear="onSearch"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="onSearch">查询</el-button>
            </el-form-item>
          </el-form>
        </div>
      </template>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="工单ID" prop="orderId" width="90" />
        <el-table-column label="工单号" prop="orderNo" width="170" />
        <el-table-column label="任务名" prop="taskName" min-width="160" show-overflow-tooltip />
        <el-table-column label="拒单原因" min-width="180">
          <template #default="{ row }">
            <span v-if="row.rejectReasonType">{{ REJECT_REASON_LABEL[row.rejectReasonType] || row.rejectReasonType }}</span>
            <span v-if="row.rejectReason">：{{ row.rejectReason }}</span>
            <span v-if="!row.rejectReasonType && !row.rejectReason">-</span>
          </template>
        </el-table-column>
        <el-table-column label="拒单时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.rejectedAt) }}</template>
        </el-table-column>
        <el-table-column label="计划开始" width="140">
          <template #default="{ row }">{{ fmtTime(row.planStartAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="warning" @click="openRedispatch(row)">重新派单</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </el-card>

    <!-- 重新派单弹窗（复用派单弹窗，提交走 dispatch 接口） -->
    <dispatch-dialog
      v-if="target"
      v-model="dispatchVisible"
      mode="dispatch"
      title="重新派单"
      :prefill="{ planStartAt: target.planStartAt || undefined }"
      :submit="submitRedispatch"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pagePendingRedispatch, dispatchOrder, type PendingRedispatchItem } from '@crane/shared/api/dispatch'
import { REJECT_REASON_LABEL } from '../../utils/tags'
import { fmtTime } from '../../utils/format'
import DispatchDialog from '../../components/DispatchDialog.vue'

const keyword = ref('')
const query = reactive({ pageNum: 1, pageSize: 20 })
const list = ref<PendingRedispatchItem[]>([])
const total = ref(0)
const loading = ref(false)

const dispatchVisible = ref(false)
const target = ref<PendingRedispatchItem | null>(null)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const page = await pagePendingRedispatch({
      keyword: keyword.value || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = page.list
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '待重派队列加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  load()
}

function openRedispatch(row: PendingRedispatchItem) {
  target.value = row
  dispatchVisible.value = true
}

async function submitRedispatch(payload: {
  staffId: number
  vehicleId: number
  planStartAt: string
  remark: string
  confirmPast: boolean
}) {
  if (!target.value) return
  await dispatchOrder(target.value.orderId, {
    staffId: payload.staffId,
    vehicleId: payload.vehicleId,
    planStartAt: payload.planStartAt,
    confirmPast: payload.confirmPast,
    remark: payload.remark || undefined
  })
  ElMessage.success('重新派单成功，等待司机接单')
  load()
}
</script>

<style scoped>
.head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.head-row > span { color: var(--sub); font-size: 13px; font-weight: 400; }
.toolbar-form { margin-bottom: 0; }
</style>
