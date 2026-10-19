<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @open="onOpen"
  >
    <el-form ref="formRef" :model="form" label-width="100px">
      <el-alert
        v-if="mode === 'convert' && prefill?.address"
        type="success"
        :closable="false"
        show-icon
        class="demand-pos"
      >
        <template #title>
          客户已选点：<b>{{ prefill.address }}</b>
          <span v-if="prefill.longitude != null">（{{ prefill.longitude }}, {{ prefill.latitude }}）</span>
          ，转工单自动沿用该位置，如需校准可在建单后修改
        </template>
      </el-alert>
      <el-form-item label="执行司机" required>
        <el-select
          v-model="form.staffId"
          placeholder="选择司机"
          filterable
          :loading="optionsLoading"
          style="width: 100%"
        >
          <el-option
            v-for="s in staffs"
            :key="s.id"
            :value="s.id"
            :label="`${s.name}（${s.phone}）`"
          />
          <template #empty>
            <p v-if="optionsLoading" class="select-hint">数据加载中…</p>
            <p v-else-if="optionsError" class="select-hint">加载失败，请关闭弹窗重试</p>
            <p v-else class="select-hint">暂无可用司机</p>
          </template>
        </el-select>
      </el-form-item>
      <el-form-item label="施工车辆" required>
        <el-select
          v-model="form.vehicleId"
          placeholder="选择空闲车辆"
          filterable
          :loading="optionsLoading"
          style="width: 100%"
        >
          <el-option
            v-for="v in idleVehicles"
            :key="v.id"
            :value="v.id"
            :label="`${v.plateNo}｜${vehicleSpecLabel(v)}｜空闲`"
          />
          <template #empty>
            <p v-if="optionsLoading" class="select-hint">数据加载中…</p>
            <p v-else-if="optionsError" class="select-hint">加载失败，请关闭弹窗重试</p>
            <p v-else class="select-hint">暂无空闲车辆</p>
          </template>
        </el-select>
        <div class="tip">仅展示空闲（IDLE）车辆；忙碌、维保车辆不可派</div>
      </el-form-item>
      <el-form-item label="计划开始" required>
        <div class="plan-start">
          <el-date-picker
            v-model="form.planStartAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="选择计划开始时间"
            style="width: 100%"
          />
          <div class="quick-dates">
            <el-button size="small" @click="setQuickDate(0)">今天</el-button>
            <el-button size="small" @click="setQuickDate(1)">明天</el-button>
            <el-button size="small" @click="setQuickDate(2)">后天</el-button>
          </div>
        </div>
      </el-form-item>
      <template v-if="mode === 'convert'">
        <el-form-item label="吊车吨位" required>
          <el-input-number v-model="form.craneTonnage" :min="1" :precision="1" controls-position="right" style="width: 200px" />
          <span class="unit">吨</span>
        </el-form-item>
        <el-form-item label="货物重量">
          <el-input-number v-model="form.cargoWeightTon" :min="0.1" :precision="2" controls-position="right" placeholder="选填" style="width: 200px" />
          <span class="unit">吨</span>
        </el-form-item>
      </template>
      <el-form-item :label="mode === 'convert' ? '任务备注' : '备注'">
        <div class="remark-block">
          <el-alert
            v-if="mode === 'convert' && prefill?.remark"
            type="info"
            :closable="false"
            show-icon
            class="demand-desc"
          >
            <template #title>
              已带入客户需求描述，可直接补充后作为任务备注
            </template>
          </el-alert>
          <div class="remark-chips">
            <el-tag
              v-for="t in REMARK_PRESETS"
              :key="t"
              :type="form.remark.includes(t) ? 'primary' : 'info'"
              effect="plain"
              class="remark-chip"
              @click="toggleRemarkPreset(t)"
            >{{ t }}</el-tag>
          </div>
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" placeholder="任务备注（选填，已带入客户需求描述，可补充；也可点选常用项）" />
        </div>
      </el-form-item>
      <el-form-item v-if="needConfirmPast" label="">
        <el-checkbox v-model="form.confirmPast">计划时间早于当前时间，确认按此时间派单</el-checkbox>
      </el-form-item>
      <el-form-item label="前置校验">
        <el-button size="small" :loading="checking" @click="onPrecheck">执行五项校验</el-button>
      </el-form-item>
      <el-form-item v-if="checks.length" label-width="100px">
        <precheck-list :checks="checks" style="width: 100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">确认提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import {
  precheckDispatch
} from '@crane/shared/api/dispatch'
import type { CheckItem } from '@crane/shared/api/dispatch'
import { pageStaffs, type Staff, type Vehicle } from '@crane/shared/api/staff'
import { pageVehicles, vehicleSpecLabel } from '@crane/shared/api/staff'
import PrecheckList from './PrecheckList.vue'
import dayjs from 'dayjs'

/**
 * 派单 / 转工单弹窗（需求单转工单、工单派单与改派共用）。
 * submit 由调用方注入：convert 场景调 demand/convert，dispatch 场景调 dispatch/dispatch。
 */
const props = defineProps<{
  modelValue: boolean
  mode: 'convert' | 'dispatch'
  title: string
  prefill?: {
    planStartAt?: string
    craneTonnage?: number
    cargoWeightTon?: number
    remark?: string
    /** 客户提报的位置信息（转工单时带出展示） */
    address?: string
    longitude?: number
    latitude?: number
  }
  submit: (payload: {
    staffId: number
    vehicleId: number
    planStartAt: string
    craneTonnage?: number
    cargoWeightTon?: number
    remark: string
    confirmPast: boolean
  }) => Promise<void>
}>()

const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const formRef = ref<FormInstance>()
const form = reactive({
  staffId: undefined as number | undefined,
  vehicleId: undefined as number | undefined,
  planStartAt: '',
  craneTonnage: undefined as number | undefined,
  cargoWeightTon: undefined as number | undefined,
  remark: '',
  confirmPast: false
})

const staffs = ref<Staff[]>([])
const vehicles = ref<Vehicle[]>([])
const checks = ref<CheckItem[]>([])
const checking = ref(false)
const submitting = ref(false)
// 下拉选项加载态：请求期间显示加载动画，避免"暂无数据"闪烁
const optionsLoading = ref(false)
const optionsError = ref(false)

const idleVehicles = computed(() => vehicles.value.filter((v) => v.status === 'IDLE'))
const needConfirmPast = computed(() =>
  !!form.planStartAt && dayjs(form.planStartAt).isBefore(dayjs())
)

/** 任务备注常用项：点选追加、再点移除（转工单/派单共用） */
const REMARK_PRESETS = ['带吊笼', '带顶笼']

function toggleRemarkPreset(text: string) {
  if (form.remark.includes(text)) {
    form.remark = form.remark
      .split(/[，,]/)
      .map((s) => s.trim())
      .filter((s) => s && s !== text)
      .join('，')
  } else {
    form.remark = form.remark.trim() ? `${form.remark.trim().replace(/[，,]$/, '')}，${text}` : text
  }
}

// immediate：弹窗随 v-if 挂载时 modelValue 已是 true，watch 不会再触发，
// 首次打开必须立即执行 loadOptions，否则 prefill（计划时间/吨位/重量/需求描述）与司机车辆列表全空
watch(
  () => props.modelValue,
  (open) => {
    if (open) loadOptions()
  },
  { immediate: true }
)

function onOpen() {
  checks.value = []
  form.confirmPast = false
}

/** 计划开始快捷日期：仅改日期部分，时间保留已选值或默认 09:00:00 */
function setQuickDate(offsetDays: number) {
  const time = form.planStartAt ? dayjs(form.planStartAt).format('HH:mm:ss') : '09:00:00'
  form.planStartAt = dayjs().add(offsetDays, 'day').format(`YYYY-MM-DDT${time}`)
}

async function loadOptions() {
  form.staffId = undefined
  form.vehicleId = undefined
  form.planStartAt = props.prefill?.planStartAt ?? ''
  form.craneTonnage = props.prefill?.craneTonnage
  form.cargoWeightTon = props.prefill?.cargoWeightTon
  form.remark = props.prefill?.remark ?? ''
  optionsLoading.value = true
  optionsError.value = false
  try {
    const [staffPage, vehiclePage] = await Promise.all([
      pageStaffs({ pageSize: 100 }),
      pageVehicles({ pageSize: 100 })
    ])
    staffs.value = staffPage.list.filter((s) => s.position === 'DRIVER' && s.status !== 'RESIGNED')
    vehicles.value = vehiclePage.list
  } catch (e) {
    optionsError.value = true
    ElMessage.error(e instanceof Error ? e.message : '加载司机车辆失败')
  } finally {
    optionsLoading.value = false
  }
}

async function onPrecheck() {
  if (!form.staffId || !form.vehicleId || !form.planStartAt) {
    ElMessage.warning('请先选择司机、车辆与计划开始时间')
    return
  }
  checking.value = true
  try {
    const res = await precheckDispatch({
      staffId: form.staffId,
      vehicleId: form.vehicleId,
      planStartAt: form.planStartAt
    })
    checks.value = res.checks
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '校验失败')
  } finally {
    checking.value = false
  }
}

async function onSubmit() {
  if (!form.staffId) return ElMessage.warning('请选择执行司机')
  if (!form.vehicleId) return ElMessage.warning('请选择施工车辆')
  if (!form.planStartAt) return ElMessage.warning('请选择计划开始时间')
  if (props.mode === 'convert') {
    if (!form.craneTonnage) return ElMessage.warning('请填写吊车吨位')
  }
  if (needConfirmPast.value && !form.confirmPast) {
    ElMessage.warning('计划时间早于当前时间，请勾选二次确认')
    return
  }
  submitting.value = true
  try {
    await props.submit({
      staffId: form.staffId,
      vehicleId: form.vehicleId,
      planStartAt: form.planStartAt,
      craneTonnage: form.craneTonnage,
      cargoWeightTon: form.cargoWeightTon,
      remark: form.remark.trim(),
      confirmPast: form.confirmPast
    })
    emit('update:modelValue', false)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.remark-block {
  width: 100%;
}
.remark-chips {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.remark-chip {
  cursor: pointer;
}
.tip { color: var(--sub); font-size: 12px; line-height: 1.5; }
.demand-pos { margin-bottom: 14px; }
.demand-desc { margin-bottom: 8px; }
.unit { margin-left: 8px; color: var(--sub); }
.plan-start { width: 100%; }
.quick-dates { margin-top: 4px; display: flex; }
.quick-dates .el-button + .el-button { margin-left: 8px; }
.select-hint { text-align: center; color: var(--sub, #909399); font-size: 13px; padding: 14px 0; margin: 0; }
</style>
