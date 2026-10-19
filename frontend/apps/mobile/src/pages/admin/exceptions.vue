<script setup lang="ts">
// 管理员异常审批：待批复 / 已批复 / 已关闭
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import RoleTabbar from '../../components/RoleTabbar.vue'
import BottomSheet from '../../components/BottomSheet.vue'
import StatusTag from '../../components/StatusTag.vue'
import {
  pageExceptions,
  replyException,
  type ExceptionRecord
} from '@crane/shared/api/exception'
import { fmtDateTime, toastError, toastOk } from '../../utils'

const TABS = [
  { value: 'PENDING', label: '待批复' },
  { value: 'APPROVED', label: '已批复' },
  { value: 'CLOSED', label: '已关闭' }
]

const INSTRUCTIONS = [
  { value: 'CONTINUE', label: '继续施工' },
  { value: 'SUSPEND', label: '暂停工单' },
  { value: 'TERMINATE', label: '终止工单' }
]

const TYPE_LABEL: Record<string, string> = {
  RAIN: '下雨',
  PARTY_STOP: '双方停工',
  VEHICLE_FAULT: '车辆故障',
  CUSTOM: '其他原因'
}
const STATUS_LABEL: Record<string, string> = {
  PENDING: '待批复',
  APPROVED: '已批复',
  CLOSED: '已关闭'
}
const INSTRUCTION_LABEL: Record<string, string> = {
  CONTINUE: '继续施工',
  SUSPEND: '暂停工单',
  TERMINATE: '终止工单'
}

const active = ref('PENDING')
const loading = ref(false)
const list = ref<ExceptionRecord[]>([])
const total = ref(0)

const pendingCount = computed(() => (active.value === 'PENDING' ? total.value : undefined))

const sheetVisible = ref(false)
const current = ref<ExceptionRecord | null>(null)
const instruction = ref('CONTINUE')
const opinion = ref('')
const submitting = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await pageExceptions({ status: active.value, pageNum: 1, pageSize: 50 })
    list.value = res.list ?? []
    total.value = res.total ?? 0
  } catch (e) {
    toastError(e)
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function switchTab(value: string) {
  if (active.value === value) return
  active.value = value
  load()
}

function openSheet(row: ExceptionRecord) {
  current.value = row
  instruction.value = 'CONTINUE'
  opinion.value = ''
  sheetVisible.value = true
}

async function submit(approve: boolean) {
  if (!current.value || submitting.value) return
  if (approve && !instruction.value) {
    uni.showToast({ title: '请选择批复指令', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await replyException(current.value.id, {
      approve,
      instruction: approve ? instruction.value : undefined,
      opinion: opinion.value.trim() || undefined
    })
    toastOk(approve ? '已按指令批复' : '已驳回异常')
    sheetVisible.value = false
    await load()
  } catch (e) {
    toastError(e)
  } finally {
    submitting.value = false
  }
}

function goOrder() {
  if (!current.value) return
  sheetVisible.value = false
  uni.navigateTo({ url: `/pages/admin/order-detail?id=${current.value.orderId}` })
}

onShow(load)
</script>

<template>
  <view class="adm-page">
    <view class="adm-hero">
      <text class="adm-hero-kicker">EXCEPTION APPROVAL</text>
      <text class="adm-hero-title">
        异常审批
        <text v-if="pendingCount" class="hl">· {{ pendingCount }}</text>
      </text>
      <text class="adm-hero-sub">司机上报后工单进入受阻，批复后继续、暂停或终止</text>
    </view>

    <view class="adm-tabs">
      <view
        v-for="t in TABS"
        :key="t.value"
        class="adm-tab"
        :class="{ active: active === t.value }"
        @tap="switchTab(t.value)"
      >{{ t.label }}</view>
    </view>

    <view v-if="loading" class="adm-loading">加载中...</view>
    <view v-else-if="!list.length" class="adm-empty">当前分类暂无异常记录</view>
    <view v-else class="adm-list">
      <view v-for="x in list" :key="x.id" class="adm-item" @tap="openSheet(x)">
        <view class="adm-item-head">
          <text class="adm-item-no">工单 #{{ x.orderId }}</text>
          <StatusTag :status="x.status" :label="STATUS_LABEL[x.status] || x.status" />
        </view>
        <text class="adm-item-title">{{ x.typeLabel || TYPE_LABEL[x.type] || x.type }}</text>
        <view class="adm-item-row">
          <text class="k">描述</text>
          <text class="v">{{ x.description || '无' }}</text>
        </view>
        <view class="adm-item-foot">
          <text class="adm-time">{{ fmtDateTime(x.reportedAt) }}</text>
          <text v-if="x.instruction" class="adm-section-link">指令：{{ INSTRUCTION_LABEL[x.instruction] || x.instruction }}</text>
          <text v-else class="adm-section-link">点击批复</text>
        </view>
      </view>
    </view>

    <BottomSheet v-if="sheetVisible" title="异常批复" @close="sheetVisible = false">
      <view v-if="current" class="adm-form">
        <view class="adm-info">
          工单 #{{ current.orderId }} · {{ current.typeLabel || TYPE_LABEL[current.type] || current.type }}
        </view>
        <view class="adm-item-row">
          <text class="k">异常描述</text>
          <text class="v">{{ current.description || '无' }}</text>
        </view>

        <view class="adm-field">
          <text class="adm-label">批复指令</text>
          <view class="instruction-row">
            <view
              v-for="i in INSTRUCTIONS"
              :key="i.value"
              class="instruction"
              :class="{ active: instruction === i.value }"
              @tap="instruction = i.value"
            >{{ i.label }}</view>
          </view>
        </view>

        <view class="adm-field">
          <text class="adm-label">批复意见（选填）</text>
          <textarea v-model="opinion" class="adm-textarea" :maxlength="500" placeholder="填写处理意见" />
        </view>

        <view class="adm-btn-row">
          <button class="adm-btn plain-danger" :disabled="submitting" @tap="submit(false)">驳回异常</button>
          <button class="adm-btn primary" :disabled="submitting" @tap="submit(true)">
            {{ submitting ? '提交中...' : '按指令批复' }}
          </button>
        </view>
        <button class="adm-btn ghost" @tap="goOrder">查看关联工单</button>
      </view>
    </BottomSheet>

    <RoleTabbar role="admin" current="pages/admin/exceptions" />
  </view>
</template>

<style scoped>
.instruction-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.instruction {
  flex: 1;
  min-width: 90px;
  text-align: center;
  padding: 10px 8px;
  border-radius: 10px;
  font-size: 13px;
  color: #475569;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}
.instruction.active {
  color: #fff;
  background: linear-gradient(135deg, #0b1f3a, #0e7490);
  border-color: transparent;
}
</style>
