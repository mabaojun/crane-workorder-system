<template>
  <el-dialog
    :model-value="modelValue"
    title="异常批复"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @open="onOpen"
  >
    <el-descriptions v-if="record" :column="1" border size="small" class="brief">
      <el-descriptions-item label="异常类型">{{ typeName }}</el-descriptions-item>
      <el-descriptions-item label="异常描述">{{ record.description || '无' }}</el-descriptions-item>
      <el-descriptions-item label="上报时间">{{ fmtTime(record.reportedAt) }}</el-descriptions-item>
    </el-descriptions>
    <el-form label-width="90px">
      <el-form-item label="批复结论" required>
        <el-radio-group v-model="form.approve">
          <el-radio :value="true">同意上报，按指令执行</el-radio>
          <el-radio :value="false">驳回上报，仅记录</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.approve" label="处理指令" required>
        <el-radio-group v-model="form.instruction">
          <el-radio value="CONTINUE">继续施工</el-radio>
          <el-radio value="SUSPEND">暂停</el-radio>
          <el-radio value="TERMINATE">终止</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="批复意见">
        <el-input v-model="form.opinion" type="textarea" :rows="3" maxlength="500" placeholder="批复意见（可选）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">确认批复</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { ExceptionRecord } from '@crane/shared/api/exception'
import { EXCEPTION_TYPE_LABEL } from '../utils/tags'
import { fmtTime } from '../utils/format'

/** 异常批复弹窗（异常处理列表与工单详情共用） */
const props = defineProps<{
  modelValue: boolean
  record: ExceptionRecord | null
  submit: (payload: { approve: boolean; instruction?: string; opinion: string }) => Promise<void>
}>()

const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const form = reactive({
  approve: true,
  instruction: 'CONTINUE',
  opinion: ''
})
const submitting = ref(false)

const typeName = computed(() => {
  if (!props.record) return '-'
  return props.record.type === 'CUSTOM'
    ? (props.record.typeLabel || EXCEPTION_TYPE_LABEL.CUSTOM)
    : (EXCEPTION_TYPE_LABEL[props.record.type] || props.record.type)
})

function onOpen() {
  form.approve = true
  form.instruction = 'CONTINUE'
  form.opinion = ''
}

async function onSubmit() {
  if (form.approve && !form.instruction) {
    ElMessage.warning('请选择处理指令')
    return
  }
  submitting.value = true
  try {
    await props.submit({
      approve: form.approve,
      instruction: form.approve ? form.instruction : undefined,
      opinion: form.opinion.trim()
    })
    emit('update:modelValue', false)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批复失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.brief { margin-bottom: 16px; }
</style>
