<template>
  <div>
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-form inline class="toolbar-form" @submit.prevent="onSearch">
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable placeholder="全部" style="width: 150px" @change="onSearch">
              <el-option v-for="(label, key) in WORK_ORDER_STATUS_LABEL" :key="key" :label="label" :value="key" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键词">
            <el-input
              v-model="query.keyword"
              placeholder="工单号 / 任务名 / 客户"
              clearable
              style="width: 220px"
              @keyup.enter="onSearch"
              @clear="onSearch"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="onReset">重置</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="openCreate">直建工单</el-button>
      </div>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="工单号" prop="orderNo" width="170" />
        <el-table-column label="任务名" prop="taskName" min-width="160" show-overflow-tooltip />
        <el-table-column label="客户" prop="customerName" min-width="140" show-overflow-tooltip />
        <el-table-column label="司机" prop="staffName" width="100">
          <template #default="{ row }">{{ row.staffName || '-' }}</template>
        </el-table-column>
        <el-table-column label="车辆" prop="vehiclePlateNo" width="110">
          <template #default="{ row }">{{ row.vehiclePlateNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="计划开始" width="140">
          <template #default="{ row }">{{ fmtTime(row.expectedStartAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <status-tag kind="workorder" :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="客户可见状态" width="120">
          <template #default="{ row }">{{ row.customerVisibleStatusLabel || row.customerVisibleStatus || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="goDetail(row)">详情</el-button>
            <el-button
              v-if="!['COMPLETED', 'CANCELLED'].includes(row.status)"
              size="small"
              text
              type="primary"
              @click="openEdit(row)"
            >修改</el-button>
            <el-button
              v-if="row.status === 'PENDING_ACCEPT'"
              size="small"
              text
              type="warning"
              @click="openDispatch(row)"
            >派单</el-button>
            <el-button
              v-if="row.status === 'IN_PROGRESS'"
              size="small"
              text
              type="warning"
              @click="changeStatus(row, 'SUSPEND')"
            >暂停</el-button>
            <el-button
              v-if="row.status === 'SUSPENDED'"
              size="small"
              text
              type="success"
              @click="changeStatus(row, 'RESUME')"
            >恢复</el-button>
            <el-button
              v-if="!['COMPLETED', 'CANCELLED'].includes(row.status)"
              size="small"
              text
              type="danger"
              @click="changeStatus(row, 'CANCEL')"
            >取消</el-button>
            <el-button
              v-if="!['COMPLETED', 'CANCELLED'].includes(row.status)"
              size="small"
              text
              type="primary"
              @click="onSettle(row)"
            >结单</el-button>
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

    <!-- 地图选点弹窗 -->
    <amap-picker
      v-model:visible="pickerVisible"
      :lng="createForm.longitude"
      :lat="createForm.latitude"
      :address="createForm.address"
      @confirm="onPicked"
    />

    <!-- 派单 / 改派弹窗 -->
    <dispatch-dialog
      v-if="dispatchTarget"
      v-model="dispatchVisible"
      mode="dispatch"
      title="派单"
      :prefill="dispatchPrefill"
      :submit="submitDispatch"
    />

    <!-- 修改工单弹窗 -->
    <work-order-edit-dialog
      v-if="editTarget"
      v-model:visible="editVisible"
      :order="editTarget"
      @updated="load"
    />

    <!-- 直建工单弹窗 -->
    <el-dialog
      v-model="createVisible"
      title="直建工单"
      width="640px"
      :close-on-click-modal="false"
      @open="loadCreateOptions"
    >
      <el-form ref="createFormRef" :model="createForm" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="任务名称" required>
              <el-input v-model="createForm.taskName" maxlength="100" placeholder="任务名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户" required>
              <el-select v-model="createForm.customerId" filterable placeholder="选择客户" style="width: 100%">
                <el-option v-for="c in customers" :key="c.id" :value="c.id" :label="`${c.name}（${c.customerNo}）`" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行司机" required>
              <el-select v-model="createForm.staffId" filterable placeholder="选择司机" style="width: 100%">
                <el-option v-for="s in staffs" :key="s.id" :value="s.id" :label="`${s.name}（${s.phone}）`" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="施工车辆" required>
              <el-select v-model="createForm.vehicleId" filterable placeholder="选择空闲车辆" style="width: 100%">
                <el-option
                  v-for="v in idleVehicles"
                  :key="v.id"
                  :value="v.id"
                  :label="`${v.plateNo}｜${v.tonnage}吨｜空闲`"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划开始" required>
              <div class="plan-start">
                <el-date-picker
                  v-model="createForm.planStartAt"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  format="YYYY-MM-DD HH:mm"
                  placeholder="计划开始时间"
                  style="width: 100%"
                />
                <div class="quick-dates">
                  <el-button size="small" @click="setQuickDate(0)">今天</el-button>
                  <el-button size="small" @click="setQuickDate(1)">明天</el-button>
                  <el-button size="small" @click="setQuickDate(2)">后天</el-button>
                </div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="吊车吨位" required>
              <el-input-number v-model="createForm.craneTonnage" :min="1" :precision="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="施工地址" required>
              <el-input v-model="createForm.address" maxlength="255" placeholder="施工地址">
                <template #append>
                  <el-button @click="pickerVisible = true">地图选点</el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="经度" required>
              <el-input-number v-model="createForm.longitude" :precision="7" :controls="false" placeholder="经度" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度" required>
              <el-input-number v-model="createForm.latitude" :precision="7" :controls="false" placeholder="纬度" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="货物名称" required>
              <el-input v-model="createForm.cargoName" maxlength="128" placeholder="货物名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="货物数量" required>
              <el-input-number v-model="createForm.cargoQty" :min="1" :precision="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="货物重量" required>
              <el-input-number v-model="createForm.cargoWeightTon" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
              <span class="unit">吨</span>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="500" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="前置校验">
              <el-button size="small" :loading="checking" @click="onPrecheck">执行五项校验</el-button>
            </el-form-item>
            <el-form-item v-if="checks.length" label-width="20px">
              <precheck-list :checks="checks" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col v-if="needConfirmPast" :span="24">
            <el-form-item label="">
              <el-checkbox v-model="createForm.confirmPast">计划时间早于当前时间，确认按此时间建单</el-checkbox>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">确认建单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WORK_ORDER_STATUS_LABEL } from '@crane/shared'
import {
  pageWorkOrders,
  createWorkOrder,
  changeWorkOrderStatus,
  settleWorkOrder,
  type WorkOrder
} from '@crane/shared/api/workorder'
import { precheckDispatch, type CheckItem } from '@crane/shared/api/dispatch'
import { pageStaffs, pageVehicles, type Staff, type Vehicle } from '@crane/shared/api/staff'
import { pageCustomers, type Customer } from '@crane/shared/api/customer'
import { fmtTime } from '../../utils/format'
import { dispatchOrder } from '@crane/shared/api/dispatch'
import dayjs from 'dayjs'
import DispatchDialog from '../../components/DispatchDialog.vue'
import WorkOrderEditDialog from '../../components/WorkOrderEditDialog.vue'
import PrecheckList from '../../components/PrecheckList.vue'
import StatusTag from '../../components/StatusTag.vue'
import AMapPicker from '../../components/AMapPicker.vue'
import type { AmapPickResult } from '@crane/shared/api/map'

const route = useRoute()
const router = useRouter()

const query = reactive({
  status: (route.query.status as string) || '',
  keyword: '',
  pageNum: 1,
  pageSize: 20
})
const list = ref<WorkOrder[]>([])
const total = ref(0)
const loading = ref(false)

// 派单弹窗
const dispatchVisible = ref(false)
const dispatchTarget = ref<WorkOrder | null>(null)
const dispatchPrefill = ref<{ planStartAt?: string }>({})

// 修改工单弹窗
const editVisible = ref(false)
const editTarget = ref<WorkOrder | null>(null)

// 直建弹窗
const createVisible = ref(false)
const creating = ref(false)
const checking = ref(false)
const checks = ref<CheckItem[]>([])
const staffs = ref<Staff[]>([])
const vehicles = ref<Vehicle[]>([])
const customers = ref<Customer[]>([])
const pickerVisible = ref(false)
const createForm = reactive({
  taskName: '',
  customerId: undefined as number | undefined,
  staffId: undefined as number | undefined,
  vehicleId: undefined as number | undefined,
  planStartAt: '',
  address: '',
  longitude: 117.1205 as number | undefined,
  latitude: 36.6519 as number | undefined,
  cargoName: '',
  cargoQty: 1,
  cargoWeightTon: undefined as number | undefined,
  craneTonnage: undefined as number | undefined,
  remark: '',
  confirmPast: false
})

const idleVehicles = computed(() => vehicles.value.filter((v) => v.status === 'IDLE'))
const needConfirmPast = computed(() =>
  !!createForm.planStartAt && new Date(createForm.planStartAt).getTime() < Date.now()
)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const page = await pageWorkOrders({
      status: query.status || undefined,
      keyword: query.keyword || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = page.list
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '工单加载失败')
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
  query.keyword = ''
  query.pageNum = 1
  load()
}

function goDetail(row: WorkOrder) {
  router.push(`/workorder/detail/${row.id}`)
}

// ==================== 修改工单 ====================
function openEdit(row: WorkOrder) {
  editTarget.value = row
  editVisible.value = true
}

// ==================== 派单 / 改派 ====================
function openDispatch(row: WorkOrder) {
  dispatchTarget.value = row
  dispatchPrefill.value = { planStartAt: row.expectedStartAt || undefined }
  dispatchVisible.value = true
}

async function submitDispatch(payload: {
  staffId: number
  vehicleId: number
  planStartAt: string
  remark: string
  confirmPast: boolean
}) {
  if (!dispatchTarget.value) return
  await dispatchOrder(dispatchTarget.value.id, {
    staffId: payload.staffId,
    vehicleId: payload.vehicleId,
    planStartAt: payload.planStartAt,
    confirmPast: payload.confirmPast,
    remark: payload.remark || undefined
  })
  ElMessage.success('派单成功，等待司机接单')
  load()
}

// ==================== 状态操作 ====================
async function changeStatus(row: WorkOrder, action: 'CANCEL' | 'SUSPEND' | 'RESUME') {
  const actionLabel = { CANCEL: '取消', SUSPEND: '暂停', RESUME: '恢复' }[action]
  try {
    const { value } = await ElMessageBox.prompt(
      `确认${actionLabel}工单 ${row.orderNo}？${action === 'RESUME' ? '' : '请填写原因：'}`,
      `${actionLabel}工单`,
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        inputPlaceholder: action === 'RESUME' ? '原因（可选）' : '原因',
        inputValidator: (v: string) =>
          action === 'RESUME' || (v && v.trim()) ? true : '原因不能为空'
      }
    )
    await changeWorkOrderStatus(row.id, { action, reason: value.trim() || undefined })
    ElMessage.success(`${actionLabel}成功`)
    load()
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

async function onSettle(row: WorkOrder) {
  try {
    await ElMessageBox.confirm(
      `兜底结单将直接把工单 ${row.orderNo} 置为已完成并开放客户评价，确认执行？`,
      '兜底结单确认',
      { type: 'warning', confirmButtonText: '确认结单', cancelButtonText: '再想想' }
    )
  } catch {
    return
  }
  try {
    await settleWorkOrder(row.id)
    ElMessage.success('结单成功')
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '结单失败')
  }
}

// ==================== 直建工单 ====================
/** 计划开始快捷日期：仅改日期部分，时间保留已选值或默认 09:00:00 */
function setQuickDate(offsetDays: number) {
  const time = createForm.planStartAt
    ? dayjs(createForm.planStartAt).format('HH:mm:ss')
    : '09:00:00'
  createForm.planStartAt = dayjs().add(offsetDays, 'day').format(`YYYY-MM-DDT${time}`)
}

/** 地图选点确认回填经纬度与地址 */
function onPicked(payload: AmapPickResult) {
  createForm.longitude = payload.lng
  createForm.latitude = payload.lat
  if (payload.address) createForm.address = payload.address
}

function openCreate() {
  checks.value = []
  createForm.taskName = ''
  createForm.customerId = undefined
  createForm.staffId = undefined
  createForm.vehicleId = undefined
  createForm.planStartAt = ''
  createForm.address = ''
  createForm.longitude = 117.1205
  createForm.latitude = 36.6519
  createForm.cargoName = ''
  createForm.cargoQty = 1
  createForm.cargoWeightTon = undefined
  createForm.craneTonnage = undefined
  createForm.remark = ''
  createForm.confirmPast = false
  createVisible.value = true
}

async function loadCreateOptions() {
  try {
    const [staffPage, vehiclePage, customerPage] = await Promise.all([
      pageStaffs({ pageSize: 100 }),
      pageVehicles({ pageSize: 100 }),
      pageCustomers({ pageSize: 100 })
    ])
    staffs.value = staffPage.list.filter((s) => s.position === 'DRIVER' && s.status !== 'RESIGNED')
    vehicles.value = vehiclePage.list
    customers.value = customerPage.list
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '基础数据加载失败')
  }
}

async function onPrecheck() {
  if (!createForm.staffId || !createForm.vehicleId || !createForm.planStartAt) {
    ElMessage.warning('请先选择司机、车辆与计划开始时间')
    return
  }
  checking.value = true
  try {
    const res = await precheckDispatch({
      staffId: createForm.staffId,
      vehicleId: createForm.vehicleId,
      planStartAt: createForm.planStartAt
    })
    checks.value = res.checks
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '校验失败')
  } finally {
    checking.value = false
  }
}

async function submitCreate() {
  if (!createForm.taskName.trim()) return ElMessage.warning('请填写任务名称')
  if (!createForm.customerId) return ElMessage.warning('请选择客户')
  if (!createForm.staffId) return ElMessage.warning('请选择执行司机')
  if (!createForm.vehicleId) return ElMessage.warning('请选择施工车辆')
  if (!createForm.planStartAt) return ElMessage.warning('请选择计划开始时间')
  if (!createForm.address.trim()) return ElMessage.warning('请填写施工地址')
  if (createForm.longitude == null || createForm.latitude == null) return ElMessage.warning('请填写经纬度')
  if (!createForm.cargoName.trim()) return ElMessage.warning('请填写货物名称')
  if (!createForm.cargoQty || createForm.cargoQty < 1) return ElMessage.warning('货物数量须大于等于 1')
  if (!createForm.cargoWeightTon) return ElMessage.warning('请填写货物重量')
  if (!createForm.craneTonnage) return ElMessage.warning('请填写吊车吨位')
  if (needConfirmPast.value && !createForm.confirmPast) {
    ElMessage.warning('计划时间早于当前时间，请勾选二次确认')
    return
  }
  creating.value = true
  try {
    await createWorkOrder({
      taskName: createForm.taskName.trim(),
      source: 'DIRECT',
      customerId: createForm.customerId,
      staffId: createForm.staffId,
      vehicleId: createForm.vehicleId,
      planStartAt: createForm.planStartAt,
      address: createForm.address.trim(),
      longitude: createForm.longitude,
      latitude: createForm.latitude,
      cargoName: createForm.cargoName.trim(),
      cargoQty: createForm.cargoQty,
      cargoWeightTon: createForm.cargoWeightTon,
      craneTonnage: createForm.craneTonnage,
      remark: createForm.remark.trim() || undefined,
      confirmPast: createForm.confirmPast
    })
    ElMessage.success('建单成功')
    createVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '建单失败')
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.unit { margin-left: 8px; color: var(--sub); }
.plan-start { width: 100%; }
.quick-dates { margin-top: 4px; display: flex; gap: 0; }
.quick-dates .el-button + .el-button { margin-left: 8px; }
</style>
