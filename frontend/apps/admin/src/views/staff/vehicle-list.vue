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
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.type === 'LIFT' ? 'warning' : 'primary'" effect="plain">
              {{ row.type === 'LIFT' ? '举升车' : '吊车' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规格" width="110" align="right">
          <template #default="{ row }">{{ vehicleSpecLabel(row) }}</template>
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
        <el-form-item label="车辆类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio-button value="CRANE">吊车</el-radio-button>
            <el-radio-button value="LIFT">举升车</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.type === 'CRANE'" label="吊车吨位" prop="tonnage">
          <el-input-number v-model="form.tonnage" :min="1" :precision="1" controls-position="right" style="width: 200px" />
          <span class="unit">吨</span>
        </el-form-item>
        <el-form-item v-else label="举升高度" prop="liftHeight">
          <el-input-number v-model="form.liftHeight" :min="1" :precision="1" controls-position="right" style="width: 200px" />
          <span class="unit">米</span>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { VEHICLE_STATUS_LABEL } from '@crane/shared'
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
  defaultStaffId: undefined as number | undefined,
  remark: ''
})

const rules: FormRules = {
  plateNo: [{ required: true, message: '请填写车牌号', trigger: 'blur' }],
  tonnage: [{
    validator: (_r, _v, cb) => {
      if (form.type === 'CRANE' && form.tonnage == null) cb(new Error('请填写吨位'))
      else cb()
    },
    trigger: 'change'
  }],
  liftHeight: [{
    validator: (_r, _v, cb) => {
      if (form.type === 'LIFT' && form.liftHeight == null) cb(new Error('请填写举升高度'))
      else cb()
    },
    trigger: 'change'
  }]
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
  form.type = row?.type === 'LIFT' ? 'LIFT' : 'CRANE'
  form.tonnage = row?.tonnage ?? undefined
  form.liftHeight = row?.liftHeight ?? undefined
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
</style>
