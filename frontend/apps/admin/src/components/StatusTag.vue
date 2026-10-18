<template>
  <el-tag v-bind="props_" :class="{ 'tag-solid': props_.solid }" disable-transitions>
    {{ props_.label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  workOrderTag,
  demandTag,
  vehicleTag,
  staffTag,
  userTag,
  exceptionTag
} from '../utils/tags'

/** 状态标签：kind 决定字典，status 为枚举字面量 */
const props = defineProps<{
  kind: 'workorder' | 'demand' | 'vehicle' | 'staff' | 'user' | 'exception'
  status?: string | null
}>()

const tagFns = {
  workorder: workOrderTag,
  demand: demandTag,
  vehicle: vehicleTag,
  staff: staffTag,
  user: userTag,
  exception: exceptionTag
} as const

const props_ = computed(() => tagFns[props.kind](props.status))
</script>
