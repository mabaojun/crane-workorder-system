<template>
  <div>
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-form inline class="toolbar-form" @submit.prevent="onSearch">
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px" @change="onSearch">
              <el-option v-for="(label, key) in EXCEPTION_STATUS_LABEL" :key="key" :label="label" :value="key" />
            </el-select>
          </el-form-item>
          <el-form-item label="工单ID">
            <el-input v-model="orderIdInput" clearable placeholder="工单ID" style="width: 130px" @clear="onSearch" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="onReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="工单号" width="110">
          <template #default="{ row }">
            <el-link type="primary" @click="goOrder(row.orderId)">{{ row.orderId }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ typeName(row) }}</template>
        </el-table-column>
        <el-table-column label="描述" prop="description" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
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
        <el-table-column label="批复时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.repliedAt) }}</template>
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

    <!-- 批复弹窗（与工单详情复用同一组件） -->
    <exception-reply-dialog
      v-model="replyVisible"
      :record="replyTarget"
      :submit="submitReply"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { pageExceptions, replyException, type ExceptionRecord } from '@crane/shared/api/exception'
import { EXCEPTION_STATUS_LABEL, EXCEPTION_TYPE_LABEL, INSTRUCTION_LABEL } from '../../utils/tags'
import { fmtTime } from '../../utils/format'
import ExceptionReplyDialog from '../../components/ExceptionReplyDialog.vue'
import StatusTag from '../../components/StatusTag.vue'

const route = useRoute()
const router = useRouter()

const query = reactive({
  status: (route.query.status as string) || '',
  orderId: undefined as number | undefined,
  pageNum: 1,
  pageSize: 20
})
const orderIdInput = ref('')
const list = ref<ExceptionRecord[]>([])
const total = ref(0)
const loading = ref(false)

const replyVisible = ref(false)
const replyTarget = ref<ExceptionRecord | null>(null)

onMounted(() => {
  if (route.query.orderId) orderIdInput.value = String(route.query.orderId)
  load()
})

function typeName(row: ExceptionRecord) {
  return row.type === 'CUSTOM'
    ? (row.typeLabel || EXCEPTION_TYPE_LABEL.CUSTOM)
    : (EXCEPTION_TYPE_LABEL[row.type] || row.type)
}

async function load() {
  loading.value = true
  const orderId = Number(orderIdInput.value)
  try {
    const page = await pageExceptions({
      status: query.status || undefined,
      orderId: orderIdInput.value && !Number.isNaN(orderId) ? orderId : undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = page.list
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '异常记录加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  load()
}

function onReset() {
  query.status = ''
  orderIdInput.value = ''
  query.pageNum = 1
  load()
}

function goOrder(orderId: number) {
  router.push(`/workorder/detail/${orderId}`)
}

function openReply(row: ExceptionRecord) {
  replyTarget.value = row
  replyVisible.value = true
}

async function submitReply(payload: { approve: boolean; instruction?: string; opinion: string }) {
  if (!replyTarget.value) return
  await replyException(replyTarget.value.id, payload)
  ElMessage.success('批复完成')
  load()
}
</script>
