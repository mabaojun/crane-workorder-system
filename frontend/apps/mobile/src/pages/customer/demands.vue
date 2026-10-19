<script setup lang="ts">
// 客户端我的需求单（T-33）：状态列表 + 驳回原因 + 再次提报
import { computed, ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import EmptyState from '../../components/EmptyState.vue'
import StatusTag from '../../components/StatusTag.vue'
import RoleTabbar from '../../components/RoleTabbar.vue'
import { listDemands } from '../../api/modules/biz'
import type { DemandOrder } from '../../api/types'
import { fmtDateTime, toastError } from '../../utils'

const list = ref<DemandOrder[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const loading = ref(false)
const hasMore = computed(() => list.value.length < total.value)

async function load(reset = false) {
  if (loading.value) return
  loading.value = true
  try {
    if (reset) pageNum.value = 1
    const res = await listDemands({ pageNum: pageNum.value, pageSize })
    list.value = reset ? res.list ?? [] : [...list.value, ...(res.list ?? [])]
    total.value = res.total ?? 0
  } catch (e) {
    toastError(e)
  } finally {
    loading.value = false
  }
}

onShow(() => load(true))

onReachBottom(() => {
  if (hasMore.value) {
    pageNum.value += 1
    load()
  }
})

/** 再次提报：把原单内容写入草稿后跳创建页（驳回单/已关闭单均可复制重提） */
function reSubmit(d: DemandOrder) {
  uni.setStorageSync('demand_draft', d)
  uni.redirectTo({ url: `/pages/customer/demand-create?parentDemandId=${d.id}` })
}
</script>

<template>
  <view class="page">
    <view class="content">
      <EmptyState v-if="!loading && !list.length" text="暂无需求单" hint="可在「需求提报」页提交吊装需求" />
      <view v-else class="list">
        <view v-for="d in list" :key="d.id" class="card">
          <view class="head">
            <text class="no">{{ d.demandNo }}</text>
            <StatusTag :status="d.status" />
          </view>
          <view class="row"><text class="k">地址</text><text class="addr">{{ d.address }}</text></view>
          <view class="row"><text class="k">期望开始</text><text>{{ fmtDateTime(d.expectedStartAt) }}</text></view>
          <view v-if="d.description" class="row"><text class="k">描述</text><text class="addr">{{ d.description }}</text></view>
          <view v-if="d.status === 'REJECTED' && d.reviewRemark" class="reject">
            <text class="k">驳回原因</text>
            <text class="addr">{{ d.reviewRemark }}</text>
          </view>
          <view class="foot">
            <text class="time">提报于 {{ fmtDateTime(d.createdAt) }}</text>
            <view class="resubmit" @tap="reSubmit(d)">再次提报</view>
          </view>
        </view>
        <view v-if="loading" class="loading">加载中...</view>
        <view v-else-if="!hasMore && list.length" class="loading">没有更多了</view>
      </view>
    </view>

    <RoleTabbar role="customer" current="pages/customer/demands" />
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
.content {
  flex: 1;
  padding: 14px 16px calc(96px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.card {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  padding: 15px 18px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.no {
  font-size: 14px;
  color: var(--sub);
  font-variant-numeric: tabular-nums;
}
.row {
  display: flex;
  gap: 8px;
  font-size: 15px;
  margin-bottom: 6px;
  line-height: 1.55;
}
.k {
  color: var(--sub);
  font-size: 14px;
  flex: none;
  width: 70px;
}
.addr {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.reject {
  background: #fef2f2;
  border-radius: var(--radius);
  padding: 10px 12px;
  font-size: 14px;
  color: var(--danger);
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 10px 0 4px;
}
.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}
.time {
  font-size: 13px;
  color: var(--sub);
}
.resubmit {
  font-size: 15px;
  font-weight: 600;
  color: var(--brand);
  padding: 0 16px;
  min-height: 44px;
  display: flex;
  align-items: center;
  border: 1px solid var(--brand);
  border-radius: 999px;
}
.loading {
  text-align: center;
  color: var(--sub);
  font-size: 14px;
  padding: 14px 0;
}
</style>
