<script setup lang="ts">
// 管理员需求审批列表：待处理 / 处理中 / 已转单 / 已驳回
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import RoleTabbar from '../../components/RoleTabbar.vue'
import StatusTag from '../../components/StatusTag.vue'
import { pageDemands, type DemandOrder } from '@crane/shared/api/demand'
import { fmtDateTime, toastError } from '../../utils'

const TABS = [
  { value: 'PENDING', label: '待处理' },
  { value: 'REVIEWING', label: '处理中' },
  { value: 'CONVERTED', label: '已转单' },
  { value: 'REJECTED', label: '已驳回' }
]

const active = ref('PENDING')
const loading = ref(false)
const list = ref<DemandOrder[]>([])
const total = ref(0)

const pendingCount = computed(() => (active.value === 'PENDING' ? total.value : undefined))

async function load() {
  loading.value = true
  try {
    const res = await pageDemands({ status: active.value, pageNum: 1, pageSize: 50 })
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

function goDetail(d: DemandOrder) {
  uni.navigateTo({ url: `/pages/admin/demand-detail?id=${d.id}` })
}

onShow(load)
</script>

<template>
  <view class="adm-page">
    <view class="adm-hero">
      <text class="adm-hero-kicker">DEMAND APPROVAL</text>
      <text class="adm-hero-title">
        需求审批
        <text v-if="pendingCount" class="hl">· {{ pendingCount }}</text>
      </text>
      <text class="adm-hero-sub">复核客户需求，确认吨位与时间后转为工单并下发</text>
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
    <view v-else-if="!list.length" class="adm-empty">当前分类暂无需求单</view>
    <view v-else class="adm-list">
      <view v-for="d in list" :key="d.id" class="adm-item" @tap="goDetail(d)">
        <view class="adm-item-head">
          <text class="adm-item-no">{{ d.demandNo }}</text>
          <StatusTag :status="d.status" />
        </view>
        <text class="adm-item-title">{{ d.contactName || '客户需求' }} · {{ d.address || '未填地址' }}</text>
        <view class="adm-item-meta">
          <text v-if="d.craneTonnage">吊车 {{ d.craneTonnage }} 吨</text>
          <text v-if="d.cargoWeight">货物 {{ d.cargoWeight }} 吨</text>
          <text>{{ fmtDateTime(d.expectedStartAt) }}</text>
        </view>
        <view class="adm-item-foot">
          <text class="adm-time">{{ d.contactPhone || '无联系电话' }}</text>
          <text class="adm-section-link">查看并处理</text>
        </view>
      </view>
    </view>

    <RoleTabbar role="admin" current="pages/admin/demands" />
  </view>
</template>
