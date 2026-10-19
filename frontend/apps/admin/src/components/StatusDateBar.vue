<template>
  <div class="date-bar">
    <el-button text class="shift" @click="select(shiftDate(modelValue, -1))">前一天</el-button>
    <el-date-picker
      v-model="inner"
      type="date"
      value-format="YYYY-MM-DD"
      :clearable="false"
      class="picker"
    />
    <el-button text class="shift" @click="select(shiftDate(modelValue, 1))">后一天</el-button>
    <div class="quick">
      <el-button
        v-for="q in quicks"
        :key="q.value"
        size="small"
        :type="modelValue === q.value ? 'primary' : 'default'"
        :plain="modelValue !== q.value"
        @click="select(q.value)"
      >{{ q.label }}</el-button>
    </div>
    <span class="hint">{{ dateHint(modelValue) }} · {{ weekdayText(modelValue) }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { dateHint, shiftDate, todayStr, weekdayText } from '../utils/status'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'change', value: string): void
}>()

const quicks = computed(() => [
  { label: '今天', value: todayStr() },
  { label: '明天', value: shiftDate(todayStr(), 1) },
  { label: '后天', value: shiftDate(todayStr(), 2) }
])

const inner = computed({
  get: () => props.modelValue,
  set: (value: string) => select(value)
})

function select(value: string) {
  if (!value || value === props.modelValue) return
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<style scoped>
.date-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.shift {
  font-size: 13px;
}
.picker {
  width: 150px;
}
.quick {
  display: flex;
  gap: 6px;
}
.hint {
  font-size: 12px;
  color: var(--sub);
  margin-left: 2px;
}
</style>
