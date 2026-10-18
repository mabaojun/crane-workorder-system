<template>
  <el-dialog
    :model-value="visible"
    :title="`修改工单（${order.orderNo}）`"
    width="680px"
    :close-on-click-modal="false"
    @open="onOpen"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <el-form :model="form" label-width="100px">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="任务名称" required>
            <el-input v-model="form.taskName" maxlength="100" placeholder="任务名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="计划开始" required>
            <div class="plan-start">
              <el-date-picker
                v-model="form.planStartAt"
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
          <el-form-item label="执行司机">
            <el-select v-model="form.staffId" filterable placeholder="保持不变" style="width: 100%">
              <el-option
                v-for="s in staffOptions"
                :key="s.id"
                :value="s.id"
                :label="`${s.name}（${s.phone}）｜${STAFF_STATUS_LABEL[s.status] || s.status}`"
              />
            </el-select>
            <div class="tip">仅可改派为无进行中工单的司机</div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="施工车辆">
            <el-select v-model="form.vehicleId" filterable placeholder="保持不变" style="width: 100%">
              <el-option
                v-for="v in selectableVehicles"
                :key="v.id"
                :value="v.id"
                :label="`${v.plateNo}｜${v.tonnage}吨｜${vehicleLabel(v)}`"
              />
            </el-select>
            <div class="tip">仅可改派为空闲（IDLE）车辆；本单当前车辆始终可选</div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="吊车吨位">
            <el-input-number v-model="form.craneTonnage" :min="0.1" :precision="1" controls-position="right" style="width: 100%" />
            <span class="unit">吨</span>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="货物重量">
            <el-input-number v-model="form.cargoWeightTon" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
            <span class="unit">吨</span>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="施工地址">
            <el-input v-model="form.address" maxlength="255" placeholder="施工地址">
              <template #append>
                <el-button @click="pickerVisible = true">地图选点</el-button>
              </template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="经度">
            <el-input-number v-model="form.longitude" :precision="7" :controls="false" placeholder="经度" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="纬度">
            <el-input-number v-model="form.latitude" :precision="7" :controls="false" placeholder="纬度" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="施工要求">
            <el-input v-model="form.requirement" type="textarea" :rows="2" maxlength="500" />
          </el-form-item>
        </el-col>
        <el-col v-if="showConfirmPast" :span="24">
          <el-form-item label="">
            <el-checkbox v-model="form.confirmPast">计划时间早于当前时间，确认修改为此时间</el-checkbox>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">保存修改</el-button>
    </template>
  </el-dialog>

  <!-- 地图选点弹窗 -->
  <amap-picker
    v-model:visible="pickerVisible"
    :lng="form.longitude"
    :lat="form.latitude"
    :address="form.address"
    @confirm="onPicked"
  />
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { updateOrder, type WorkOrder, type WorkOrderUpdateRequest } from '@crane/shared/api/workorder'
import { pageStaffs, pageVehicles, type Staff, type Vehicle } from '@crane/shared/api/staff'
import { ApiError } from '@crane/shared/api/client'
import { VEHICLE_STATUS_LABEL } from '@crane/shared'
import type { AmapPickResult } from '@crane/shared/api/map'
import dayjs from 'dayjs'
import AMapPicker from './AMapPicker.vue'
import { STAFF_STATUS_LABEL } from '../utils/tags'

/**
 * 修改工单弹窗（API-WO-004 PATCH）：打开时回填当前值，提交仅传发生变更的字段。
 * 非终态校验由页面入口与后端共同保证。
 */
const props = defineProps<{
  visible: boolean
  order: WorkOrder
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'updated'): void
}>()

const form = reactive({
  taskName: '',
  planStartAt: '',
  staffId: undefined as number | undefined,
  vehicleId: undefined as number | undefined,
  craneTonnage: undefined as number | undefined,
  cargoWeightTon: undefined as number | undefined,
  address: '',
  longitude: undefined as number | undefined,
  latitude: undefined as number | undefined,
  remark: '',
  requirement: '',
  confirmPast: false
})

/** 打开时的原始值快照，用于计算变更字段 */
const snapshot = ref({
  taskName: '',
  planStartAt: '',
  staffId: null as number | null,
  vehicleId: null as number | null,
  craneTonnage: null as number | null,
  cargoWeightTon: null as number | null,
  address: '',
  longitude: null as number | null,
  latitude: null as number | null,
  remark: '',
  requirement: ''
})

const staffOptions = ref<Staff[]>([])
const vehicleOptions = ref<Vehicle[]>([])
const pickerVisible = ref(false)
const submitting = ref(false)
/** 后端 30004（计划时间早于当前）报错后强制展示二次确认勾选 */
const confirmPastNeeded = ref(false)

const selectableVehicles = computed(() =>
  vehicleOptions.value.filter((v) => v.status === 'IDLE' || v.id === props.order.vehicleId)
)

const needConfirmPast = computed(() =>
  !!form.planStartAt && dayjs(form.planStartAt).isBefore(dayjs())
)

const showConfirmPast = computed(() => needConfirmPast.value || confirmPastNeeded.value)

function onOpen() {
  const o = props.order
  form.taskName = o.taskName || ''
  form.planStartAt = o.expectedStartAt || ''
  form.staffId = o.staffId ?? undefined
  form.vehicleId = o.vehicleId ?? undefined
  form.craneTonnage = o.craneTonnage ?? undefined
  form.cargoWeightTon = o.cargoWeight ?? undefined
  form.address = o.address || ''
  form.longitude = o.longitude ?? undefined
  form.latitude = o.latitude ?? undefined
  form.remark = o.remark || ''
  form.requirement = o.requirement || ''
  form.confirmPast = false
  snapshot.value = {
    taskName: form.taskName,
    planStartAt: form.planStartAt,
    staffId: o.staffId,
    vehicleId: o.vehicleId,
    craneTonnage: o.craneTonnage,
    cargoWeightTon: o.cargoWeight,
    address: form.address,
    longitude: o.longitude,
    latitude: o.latitude,
    remark: form.remark,
    requirement: form.requirement
  }
  confirmPastNeeded.value = false
  loadOptions()
}

async function loadOptions() {
  try {
    const [staffPage, vehiclePage] = await Promise.all([
      pageStaffs({ pageSize: 100 }),
      pageVehicles({ pageSize: 100 })
    ])
    staffOptions.value = staffPage.list.filter((s) => s.position === 'DRIVER' && s.status !== 'RESIGNED')
    vehicleOptions.value = vehiclePage.list
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '基础数据加载失败')
  }
}

/** 计划开始快捷日期：仅改日期部分，时间保留已选值或默认 09:00:00 */
function setQuickDate(offsetDays: number) {
  const time = form.planStartAt ? dayjs(form.planStartAt).format('HH:mm:ss') : '09:00:00'
  form.planStartAt = dayjs().add(offsetDays, 'day').format(`YYYY-MM-DDT${time}`)
}

/** 地图选点确认回填经纬度与地址 */
function onPicked(payload: AmapPickResult) {
  form.longitude = payload.lng
  form.latitude = payload.lat
  if (payload.address) form.address = payload.address
}

function vehicleLabel(v: Vehicle) {
  const status = VEHICLE_STATUS_LABEL[v.status] || v.status
  return v.id === props.order.vehicleId ? `${status}｜当前` : status
}

function numChanged(a: number | undefined, b: number | null): boolean {
  return (a ?? null) !== b
}

/** 计算与原值相比发生变更的字段，无变更返回 null */
function buildBody(): WorkOrderUpdateRequest | null {
  const body: WorkOrderUpdateRequest = {}
  const taskName = form.taskName.trim()
  if (taskName !== snapshot.value.taskName) body.taskName = taskName
  const address = form.address.trim()
  if (address !== snapshot.value.address) body.address = address
  if (numChanged(form.longitude, snapshot.value.longitude) && form.longitude != null) body.longitude = form.longitude
  if (numChanged(form.latitude, snapshot.value.latitude) && form.latitude != null) body.latitude = form.latitude
  if (form.planStartAt !== snapshot.value.planStartAt) body.planStartAt = form.planStartAt
  if (numChanged(form.craneTonnage, snapshot.value.craneTonnage)) body.craneTonnage = form.craneTonnage
  if (numChanged(form.cargoWeightTon, snapshot.value.cargoWeightTon)) body.cargoWeightTon = form.cargoWeightTon
  const remark = form.remark.trim()
  if (remark !== snapshot.value.remark) body.remark = remark
  const requirement = form.requirement.trim()
  if (requirement !== snapshot.value.requirement) body.requirement = requirement
  if (form.staffId != null && form.staffId !== snapshot.value.staffId) body.staffId = form.staffId
  if (form.vehicleId != null && form.vehicleId !== snapshot.value.vehicleId) body.vehicleId = form.vehicleId
  return Object.keys(body).length ? body : null
}

async function onSubmit() {
  if (!form.taskName.trim()) return ElMessage.warning('请填写任务名称')
  if (!form.planStartAt) return ElMessage.warning('请选择计划开始时间')
  const body = buildBody()
  if (!body) return ElMessage.warning('未修改任何字段')
  if (showConfirmPast.value && !form.confirmPast) {
    ElMessage.warning('计划时间早于当前时间，请勾选二次确认')
    return
  }
  submitting.value = true
  try {
    await updateOrder(props.order.id, {
      ...body,
      confirmPast: form.confirmPast ? true : undefined
    })
    ElMessage.success('工单修改成功')
    emit('update:visible', false)
    emit('updated')
  } catch (e) {
    if (e instanceof ApiError && e.code === 30004) {
      confirmPastNeeded.value = true
      ElMessage.warning('计划时间早于当前时间，请勾选二次确认后重试')
    }
    ElMessage.error(e instanceof Error ? e.message : '修改失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.tip { color: var(--sub); font-size: 12px; line-height: 1.5; }
.unit { margin-left: 8px; color: var(--sub); }
.plan-start { width: 100%; }
.quick-dates { margin-top: 4px; display: flex; gap: 0; }
.quick-dates .el-button + .el-button { margin-left: 8px; }
</style>
