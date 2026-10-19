<template>
  <div>
    <el-card shadow="never" class="page-card">
      <div class="toolbar">
        <el-form inline class="toolbar-form" @submit.prevent="onSearch">
          <el-form-item label="车牌号">
            <el-input v-model="query.plateNo" clearable placeholder="车牌号" style="width: 160px" @clear="onSearch" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable placeholder="全部" style="width: 180px" @change="onSearch">
              <el-option v-for="(label, key) in VEHICLE_STATUS_LABEL" :key="key" :label="label" :value="key" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="onReset">重置</el-button>
          </el-form-item>
        </el-form>
        <el-button type="primary" @click="openForm()">新增车辆</el-button>
      </div>
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="车牌号" prop="plateNo" width="120" />
        <el-table-column label="型号" prop="model" min-width="140">
          <template #default="{ row }">{{ row.model || '-' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag :type="row.type === 'LIFT' ? 'warning' : row.type === 'CRANE' ? 'primary' : 'success'" effect="plain">
              {{ VEHICLE_TYPE_LABEL[row.type] || '吊车' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规格" width="110" align="right">
          <template #default="{ row }">{{ vehicleSpecLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="车架号" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.vin || '-' }}</template>
        </el-table-column>
        <el-table-column label="车险到期" width="115">
          <template #default="{ row }">
            <span :class="{ 'cert-expiring': isCertExpiring(insuranceExpireDate(row)) }">
              {{ insuranceExpireDate(row) || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="下次审车" width="115">
          <template #default="{ row }">
            <span :class="{ 'cert-expiring': isCertExpiring(inspectionNextDate(row)) }">
              {{ inspectionNextDate(row) || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="默认司机" width="120">
          <template #default="{ row }">{{ staffName(row.defaultStaffId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="160">
          <template #default="{ row }">
            <status-tag kind="vehicle" :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openForm(row)">编辑</el-button>
            <el-dropdown
              v-if="row.status !== 'BUSY'"
              trigger="click"
              @command="(status: string) => onChangeStatus(row, status)"
            >
              <el-button size="small" text type="warning">变更状态</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="opt in MANUAL_STATUS_OPTIONS"
                    :key="opt.value"
                    :command="opt.value"
                    :disabled="row.status === opt.value"
                  >{{ opt.label }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-tooltip v-else content="作业中状态由工单自动驱动" placement="top">
              <el-button size="small" text type="info" disabled>作业中</el-button>
            </el-tooltip>
            <el-button size="small" text type="danger" @click="onDelete(row)">删除</el-button>
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

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="editing ? '编辑车辆' : '新增车辆'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="车牌号" prop="plateNo">
          <el-input v-model="form.plateNo" maxlength="16" placeholder="如 鲁A12345" />
        </el-form-item>
        <el-form-item label="车辆型号">
          <el-input v-model="form.model" maxlength="64" placeholder="如 中联 25K5" />
        </el-form-item>
        <el-form-item label="车辆种类" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="opt in VEHICLE_TYPE_OPTIONS" :key="opt.value" :value="opt.value" :label="opt.label" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.type === 'CRANE'" label="吊车吨位">
          <el-input-number v-model="form.tonnage" :min="1" :precision="1" controls-position="right" style="width: 200px" />
          <span class="unit">吨（选填）</span>
        </el-form-item>
        <el-form-item v-else-if="form.type === 'LIFT'" label="举升高度">
          <el-input-number v-model="form.liftHeight" :min="1" :precision="1" controls-position="right" style="width: 200px" />
          <span class="unit">米（选填）</span>
          <div class="quick-heights">
            <span class="quick-label">常用：</span>
            <el-check-tag
              v-for="h in COMMON_LIFT_HEIGHTS"
              :key="h"
              :checked="form.liftHeight === h"
              @change="form.liftHeight = h"
            >{{ h }}米</el-check-tag>
          </div>
        </el-form-item>
        <el-form-item label="车架号">
          <el-input
            v-model="form.vin"
            maxlength="32"
            placeholder="17 位 VIN，可按年款码自动推算审车周期"
          />
        </el-form-item>
        <el-form-item label="保险购买日期">
          <el-date-picker
            v-model="form.insurancePurchaseDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 200px"
          />
          <span class="unit">到期后 1 年内提醒续保</span>
        </el-form-item>
        <el-form-item label="审车日期">
          <el-date-picker
            v-model="form.inspectionDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="最近一次审车日期"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="审车周期">
          <el-radio-group v-model="form.inspectionCycle">
            <el-radio-button value="">自动</el-radio-button>
            <el-radio-button value="HALF_YEAR">半年</el-radio-button>
            <el-radio-button value="YEAR">一年</el-radio-button>
          </el-radio-group>
          <div class="quick-heights">
            <span class="quick-label">{{ cycleHint }}</span>
          </div>
        </el-form-item>
        <el-form-item label="默认司机">
          <el-select v-model="form.defaultStaffId" clearable filterable placeholder="选择司机（可空）" style="width: 100%">
            <el-option v-for="s in drivers" :key="s.id" :value="s.id" :label="`${s.name}（${s.phone}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { VEHICLE_STATUS_LABEL, VEHICLE_TYPE_LABEL, VEHICLE_TYPE_OPTIONS } from '@crane/shared'
import {
  pageVehicles,
  createVehicle,
  updateVehicle,
  deleteVehicle,
  changeVehicleStatus,
  vehicleSpecLabel,
  pageStaffs,
  type Vehicle,
  type Staff
} from '@crane/shared/api/staff'
import StatusTag from '../../components/StatusTag.vue'

/** 常用举升高度（米），可点选也可直接输入 */
const COMMON_LIFT_HEIGHTS = [20, 30]

/** 管理员可手动切换的目标状态（BUSY 作业中由工单自动驱动） */
const MANUAL_STATUS_OPTIONS = [
  { value: 'IDLE', label: '空闲' },
  { value: 'BUSY_THIRD', label: '第三方派单外出' },
  { value: 'MAINTENANCE', label: '维保' },
  { value: 'DISABLED', label: '停用' }
]

const query = reactive({ plateNo: '', status: '', pageNum: 1, pageSize: 20 })
const list = ref<Vehicle[]>([])
const total = ref(0)
const loading = ref(false)
const drivers = ref<Staff[]>([])

const formVisible = ref(false)
const submitting = ref(false)
const editing = ref<Vehicle | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  plateNo: '',
  model: '',
  type: 'CRANE',
  tonnage: undefined as number | undefined,
  liftHeight: undefined as number | undefined,
  vin: '',
  insurancePurchaseDate: '' as string,
  inspectionDate: '' as string,
  inspectionCycle: '' as '' | 'HALF_YEAR' | 'YEAR',
  defaultStaffId: undefined as number | undefined,
  remark: ''
})

/** 按 VIN 第 10 位年款码推算车龄，车龄 >15 年提示半年一检 */
const cycleHint = computed(() => {
  if (form.inspectionCycle === 'HALF_YEAR') return '当前按半年一审执行'
  if (form.inspectionCycle === 'YEAR') return '当前按一年一审执行'
  const year = parseModelYear(form.vin)
  if (!year) return '留空自动：按车架号年款码推算（15 年以上半年，其余一年）'
  const age = new Date().getFullYear() - year
  return `按车架号推算车龄 ${age} 年，建议${age > 15 ? '半年' : '一年'}一审`
})

/** VIN 年款码：数字 1-9 → 2001-2009，字母 → 2010 起 */
function parseModelYear(vin: string): number | null {
  const code = vin.trim().charAt(9).toUpperCase()
  if (!code) return null
  if (code >= '1' && code <= '9') return 2000 + Number(code)
  const letters = 'ABCDEFGHJKLMNPRSTVWXY'
  const idx = letters.indexOf(code)
  return idx >= 0 ? 2010 + idx : null
}

/** 车险到期日 = 保险购买日期 + 1 年 */
function insuranceExpireDate(row: Vehicle): string {
  return row.insurancePurchaseDate ? addMonths(row.insurancePurchaseDate, 12) : ''
}

/** 下次审车日 = 审车日期 + 审车周期（留空按 VIN 推算，默认一年） */
function inspectionNextDate(row: Vehicle): string {
  if (!row.inspectionDate) return ''
  const cycle = row.inspectionCycle || (() => {
    const year = parseModelYear(row.vin || '')
    return year && new Date().getFullYear() - year > 15 ? 'HALF_YEAR' : 'YEAR'
  })()
  return addMonths(row.inspectionDate, cycle === 'HALF_YEAR' ? 6 : 12)
}

function addMonths(date: string, months: number): string {
  const d = new Date(`${date}T00:00:00`)
  if (Number.isNaN(d.getTime())) return ''
  d.setMonth(d.getMonth() + months)
  return d.toISOString().slice(0, 10)
}

/** 到期前 30 天内或已过期高亮 */
function isCertExpiring(date: string): boolean {
  if (!date) return false
  const diff = (new Date(`${date}T00:00:00`).getTime() - Date.now()) / 86400000
  return diff <= 30
}

const rules: FormRules = {
  plateNo: [{ required: true, message: '请填写车牌号', trigger: 'blur' }]
}

onMounted(async () => {
  await Promise.all([load(), loadDrivers()])
})

async function load() {
  loading.value = true
  try {
    const page = await pageVehicles({
      plateNo: query.plateNo || undefined,
      status: query.status || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    list.value = page.list
    total.value = page.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '车辆加载失败')
  } finally {
    loading.value = false
  }
}

async function loadDrivers() {
  try {
    const page = await pageStaffs({ pageSize: 100 })
    drivers.value = page.list.filter((s) => s.position === 'DRIVER' && s.status !== 'RESIGNED')
  } catch {
    /* 司机下拉加载失败不阻塞页面 */
  }
}

function staffName(staffId?: number | null) {
  if (!staffId) return '-'
  const s = drivers.value.find((x) => x.id === staffId)
  return s ? s.name : String(staffId)
}

function onSearch() {
  query.pageNum = 1
  load()
}

function onReset() {
  query.plateNo = ''
  query.status = ''
  query.pageNum = 1
  load()
}

function openForm(row?: Vehicle) {
  editing.value = row ?? null
  form.plateNo = row?.plateNo ?? ''
  form.model = row?.model ?? ''
  form.type = row?.type || 'CRANE'
  form.tonnage = row?.tonnage ?? undefined
  form.liftHeight = row?.liftHeight ?? undefined
  form.vin = row?.vin ?? ''
  form.insurancePurchaseDate = row?.insurancePurchaseDate ?? ''
  form.inspectionDate = row?.inspectionDate ?? ''
  form.inspectionCycle = row?.inspectionCycle === 'HALF_YEAR' || row?.inspectionCycle === 'YEAR'
    ? row.inspectionCycle
    : ''
  form.defaultStaffId = row?.defaultStaffId ?? undefined
  form.remark = row?.remark ?? ''
  formVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  const body = {
    plateNo: form.plateNo.trim(),
    model: form.model.trim() || undefined,
    type: form.type,
    tonnage: form.type === 'CRANE' ? form.tonnage : undefined,
    liftHeight: form.type === 'LIFT' ? form.liftHeight : undefined,
    vin: form.vin.trim() || undefined,
    insurancePurchaseDate: form.insurancePurchaseDate || undefined,
    inspectionDate: form.inspectionDate || undefined,
    inspectionCycle: form.inspectionCycle || undefined,
    defaultStaffId: form.defaultStaffId,
    remark: form.remark.trim() || undefined
  }
  try {
    if (editing.value) {
      await updateVehicle(editing.value.id, body)
      ElMessage.success('保存成功')
    } else {
      await createVehicle(body)
      ElMessage.success('已创建车辆')
    }
    formVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

/** 管理员手动变更状态：确认后调 PATCH，BUSY 中入口已在前端隐藏 */
async function onChangeStatus(row: Vehicle, status: string) {
  const label = MANUAL_STATUS_OPTIONS.find((o) => o.value === status)?.label ?? status
  try {
    await ElMessageBox.confirm(
      `确认将车辆「${row.plateNo}」状态变更为「${label}」？`,
      '变更状态',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await changeVehicleStatus(row.id, status)
    ElMessage.success(`已变更为「${label}」`)
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '状态变更失败')
  }
}

async function onDelete(row: Vehicle) {
  try {
    await ElMessageBox.confirm(
      `确认删除车辆「${row.plateNo}」？删除后不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteVehicle(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}
</script>

<style scoped>
.unit { margin-left: 8px; color: var(--sub); }
.quick-heights { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
.quick-label { color: var(--sub); font-size: 12px; }
.cert-expiring { color: var(--el-color-danger); font-weight: 600; }
</style>
