<script setup lang="ts">
// 管理员工单管理：按状态查看、下发/改派、进入详情处理，支持新建工单
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import RoleTabbar from '../../components/RoleTabbar.vue'
import OrderCard from '../../components/OrderCard.vue'
import { pageWorkOrders, type WorkOrder } from '@crane/shared/api/workorder'
import { toastError } from '../../utils'

const TABS = [
  { value: 'PENDING_ACCEPT', label: '待接单' },
  { value: 'PENDING_ARRIVE', label: '待到场' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'SUSPENDED', label: '受阻' },
  { value: 'COMPLETED', label: '已完成' },
  { value: '', label: '全部' }
]

const active = ref('PENDING_ACCEPT')
const loading = ref(false)
const list = ref<WorkOrder[]>([])
const total = ref(0)

async function load() {
  loading.value = true
  try {
    const res = await pageWorkOrders({
      status: active.value || undefined,
      pageNum: 1,
      pageSize: 50
    })
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

function goDetail(o: WorkOrder) {
  uni.navigateTo({ url: `/pages/admin/order-detail?id=${o.id}` })
}

function goCreate() {
  uni.navigateTo({ url: '/pages/admin/order-create' })
}

function goVoice() {
  uni.navigateTo({ url: '/pages/admin/voice-create' })
}

onLoad((opts) => {
  if (opts?.status) active.value = String(opts.status)
})

onShow(load)
</script>

<template>
  <view class="adm-page">
    <view class="adm-hero">
      <text class="adm-hero-kicker">WORK ORDER CONTROL</text>
      <text class="adm-hero-title">
        工单管理
        <text v-if="total" class="hl">· {{ total }}</text>
      </text>
      <text class="adm-hero-sub">下发、改派、取消与结单，移动端随时处理</text>
      <view class="hero-actions">
        <button class="hero-btn" @tap="goCreate">新建工单</button>
        <button class="hero-btn voice" @tap="goVoice">语音说单</button>
      </view>
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
    <view v-else-if="!list.length" class="adm-empty">当前状态暂无工单</view>
    <view v-else class="adm-list">
      <OrderCard
        v-for="o in list"
        :key="o.id"
        :order="o"
        status-key="status"
        @tap="goDetail(o)"
      />
    </view>

    <RoleTabbar role="admin" current="pages/admin/orders" />
  </view>
</template>

<style scoped>
.hero-actions {
  margin-top: 12px;
  display: flex;
  gap: 10px;
}
.hero-btn {
  display: inline-block;
  margin: 0;
  padding: 0 16px;
  height: 34px;
  line-height: 34px;
  border-radius: 999px;
  font-size: 13px;
  color: #0b1f3a;
  background: #a5f3fc;
  border: none;
}
.hero-btn.voice {
  color: #cffafe;
  background: rgba(255, 255, 255, 0.08);
  border: 1px dashed rgba(165, 243, 252, 0.45);
}
.hero-btn::after {
  border: none;
}
</style>
