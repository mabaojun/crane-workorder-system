<template>
  <div>
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-form inline class="toolbar-form" @submit.prevent="onSearch">
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable placeholder="全部" style="width: 160px" @change="onSearch">
              <el-option v-for="(label, key) in DEMAND_ORDER_STATUS_LABEL" :key="key" :label="label" :value="key" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="onReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="单号" prop="demandNo" width="170" />
        <el-table-column label="标题" min-width="200">
          <template #default="{ row }">{{ truncate(row.description, 24) }}</template>
        </el-table-column>
        <el-table-column label="联系人" prop="contactName" width="100" />
        <el-table-column label="电话" prop="contactPhone" width="130" />
        <el-table-column label="吨位" width="90" align="right">
          <template #default="{ row }">{{ row.craneTonnage != null ? `${row.craneTonnage}吨` : '-' }}</template>
        </el-table-column>
        <el-table-column label="期望时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.expectedStartAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <status-tag kind="demand" :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'PENDING' || row.status === 'REVIEWING'"
              size="small"
              text
              type="warning"
              @click="openConvert(row)"
            >转工单</el-button>
            <el-button
              v-if="row.status === 'PENDING' || row.status === 'REVIEWING'"
              size="small"
              text
              type="danger"
              @click="openReject(row)"
            >驳回</el-button>
            <el-button
              v-if="row.status === 'CONVERTED' && row.convertedOrderId"
              size="small"
              text
              type="success"
              @click="goWorkOrder(row.convertedOrderId)"
            >工单{{ row.convertedOrderId }}</el-button>
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

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="需求单详情" size="480px">
      <el-descriptions v-if="detail" :column="1" border size="small">
        <el-descriptions-item label="需求单号">{{ detail.demandNo }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ DEMAND_SOURCE_LABEL[detail.source] || detail.source }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <status-tag kind="demand" :status="detail.status" />
        </el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detail.contactName || '无' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detail.contactPhone || '无' }}</el-descriptions-item>
        <el-descriptions-item label="施工地址">{{ detail.address || '无' }}</el-descriptions-item>
        <el-descriptions-item label="经纬度">
          {{ detail.longitude ?? '-' }}, {{ detail.latitude ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="需求描述">{{ detail.description || '无' }}</el-descriptions-item>
        <el-descriptions-item label="需求图片">
          <template v-if="detailImages.length">
            <div class="img-urls">{{ detailImages.join('；') }}</div>
          </template>
          <template v-else>无</template>
        </el-descriptions-item>
        <el-descriptions-item label="意向车辆">{{ detail.intendedVehicleId ?? '无' }}</el-descriptions-item>
        <el-descriptions-item label="吊车吨位">{{ detail.craneTonnage != null ? `${detail.craneTonnage} 吨` : '未填' }}</el-descriptions-item>
        <el-descriptions-item label="货物重量">{{ detail.cargoWeight != null ? `${detail.cargoWeight} 吨` : '未填' }}</el-descriptions-item>
        <el-descriptions-item label="期望开始">{{ fmtTime(detail.expectedStartAt) }}</el-descriptions-item>
        <el-descriptions-item label="复核意见">{{ detail.reviewRemark || '无' }}</el-descriptions-item>
        <el-descriptions-item label="驳回次数">{{ detail.rejectCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="关联工单">
          <el-link v-if="detail.convertedOrderId" type="primary" @click="goWorkOrder(detail.convertedOrderId)">
            {{ detail.convertedOrderId }}
          </el-link>
          <template v-else>无</template>
        </el-descriptions-item>
        <el-descriptions-item label="暂存到期">{{ fmtTime(detail.expiresAt) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmtTime(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ fmtTime(detail.updatedAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <!-- 驳回弹窗（不同意时直接驳回，无需复核流程） -->
    <el-dialog v-model="rejectVisible" title="驳回需求单" width="480px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="驳回原因" required>
          <el-input
            v-model="rejectForm.reviewRemark"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="驳回原因（对客户可见，客户可修改后再次提报）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectSubmitting" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 转工单弹窗 -->
    <dispatch-dialog
      v-if="convertTarget"
      v-model="convertVisible"
      mode="convert"
      title="需求单转工单"
      :prefill="convertPrefill"
      :submit="submitConvert"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DEMAND_ORDER_STATUS_LABEL } from '@crane/shared'
import {
  pageDemands,
  reviewDemand,
  convertDemand,
  type DemandOrder
} from '@crane/shared/api/demand'
import { DEMAND_SOURCE_LABEL } from '../../utils/tags'
import { fmtTime, parseImageUrls } from '../../utils/format'
import DispatchDialog from '../../components/DispatchDialog.vue'
import StatusTag from '../../components/StatusTag.vue'

const route = useRoute()
const router = useRouter()

const query = reactive({
  status: (route.query.status as string) || '',
  pageNum: 1,
  pageSize: 20
})
const list = ref<DemandOrder[]>([])
const total = ref(0)
const loading = ref(false)

// 详情抽屉
const detailVisible = ref(false)
const detail = ref<DemandOrder | null>(null)
const detailImages = ref<string[]>([])

// 驳回弹窗
const rejectVisible = ref(false)
const rejectSubmitting = ref(false)
const rejectTarget = ref<DemandOrder | null>(null)
const rejectForm = reactive({ reviewRemark: '' })

// 转工单弹窗
const convertVisible = ref(false)
const convertTarget = ref<DemandOrder | null>(null)
const convertPrefill = ref<{ planStartAt?: string; craneTonnage?: number; cargoWeightTon?: number }>({})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const page = await pageDemands({ ...query, status: query.status || undefined })
    list.value = page.list
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '需求单加载失败')
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
  query.pageNum = 1
  load()
}

function truncate(val: string, len: number) {
  if (!val) return '-'
  return val.length > len ? `${val.slice(0, len)}...` : val
}

function openDetail(row: DemandOrder) {
  detail.value = row
  detailImages.value = parseImageUrls(row.imageUrls)
  detailVisible.value = true
}

function openReject(row: DemandOrder) {
  rejectTarget.value = row
  rejectForm.reviewRemark = ''
  rejectVisible.value = true
}

async function submitReject() {
  if (!rejectTarget.value) return
  if (!rejectForm.reviewRemark.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  rejectSubmitting.value = true
  try {
    await reviewDemand(rejectTarget.value.id, {
      approve: false,
      reviewRemark: rejectForm.reviewRemark.trim()
    })
    ElMessage.success('已驳回，客户可修改后再次提报')
    rejectVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '驳回失败')
  } finally {
    rejectSubmitting.value = false
  }
}

function openConvert(row: DemandOrder) {
  convertTarget.value = row
  convertPrefill.value = {
    planStartAt: row.expectedStartAt || undefined,
    craneTonnage: row.craneTonnage ?? undefined,
    cargoWeightTon: row.cargoWeight ?? undefined
  }
  convertVisible.value = true
}

async function submitConvert(payload: {
  staffId: number
  vehicleId: number
  planStartAt: string
  craneTonnage?: number
  cargoWeightTon?: number
  remark: string
}) {
  if (!convertTarget.value) return
  await convertDemand(convertTarget.value.id, {
    staffId: payload.staffId,
    vehicleId: payload.vehicleId,
    planStartAt: payload.planStartAt,
    craneTonnage: payload.craneTonnage as number,
    cargoWeightTon: payload.cargoWeightTon as number,
    remark: payload.remark
  })
  ElMessage.success('已转工单')
  load()
}

function goWorkOrder(orderId: number) {
  router.push(`/workorder/detail/${orderId}`)
}
</script>

<style scoped>
.tip { color: var(--sub); font-size: 12px; line-height: 1.5; }
.img-urls { word-break: break-all; }
</style>
