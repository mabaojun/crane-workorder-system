<script setup lang="ts">
// 司机端首页（T-31 重构）：我的工单卡片列表
// 进行中（IN_PROGRESS）最高优先级固定置顶，其余按 原有规则 排序；
// 卡片仅展示，点击统一跳转专属操作页，所有操作在工单操作页完成
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import BrandBar from '../../components/BrandBar.vue'
import OrderCard from '../../components/OrderCard.vue'
import EmptyState from '../../components/EmptyState.vue'
import RoleTabbar from '../../components/RoleTabbar.vue'
import { listOrders } from '../../api/modules/biz'
import type { WorkOrder } from '../../api/types'
import { toastError } from '../../utils'

// 活跃状态机（终态不在首页展示）
const ACTIVE_STATUSES = ['PENDING_ACCEPT', 'PENDING_ARRIVE', 'IN_PROGRESS', 'SUSPENDED']
// 展示排序：进行中置顶为最高优先级，其余按 原有规则（待接单→待到场→暂停）
const RANK: Record<string, number> = {
  IN_PROGRESS: 0,
  PENDING_ACCEPT: 1,
  PENDING_ARRIVE: 2,
  SUSPENDED: 3
}

const loading = ref(false)
const orders = ref<WorkOrder[]>([])

async function load() {
  loading.value = true
  try {
    const res = await listOrders({ pageNum: 1, pageSize: 50 })
    const actives = (res.list ?? []).filter((o) => ACTIVE_STATUSES.includes(o.status))
    actives.sort((a, b) => (RANK[a.status] ?? 9) - (RANK[b.status] ?? 9))
    orders.value = actives
  } catch (e) {
    toastError(e)
  } finally {
    loading.value = false
  }
}

onShow(load)

/** 统一跳转专属操作页：所有工单操作在该页完成 */
function goDetail(o: WorkOrder) {
  uni.navigateTo({ url: `/pages/driver/order-detail?id=${o.id}` })
}
</script>

<template>
  <view class="page">
    <BrandBar />
    <scroll-view scroll-y class="content">
      <EmptyState
        v-if="!loading && !orders.length"
        text="暂无待办工单"
        hint="有新派单时将在这里展示"
      />

      <view v-else class="list">
        <OrderCard
          v-for="o in orders"
          :key="o.id"
          :order="o"
          status-key="status"
          highlight
          @tap="goDetail(o)"
        />
      </view>

      <!-- 状态图例：颜色+文字双提示 -->
      <view class="legend">
        <view class="legend-item"><view class="dot d-progress" /><text>进行中</text></view>
        <view class="legend-item"><view class="dot d-pending" /><text>待接单</text></view>
        <view class="legend-item"><view class="dot d-arrive" /><text>待到场</text></view>
        <view class="legend-item"><view class="dot d-suspend" /><text>已暂停</text></view>
      </view>
    </scroll-view>

    <RoleTabbar role="driver" current="pages/driver/index" />
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
  padding: 12px 16px 88px;
  box-sizing: border-box;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.legend {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 16px;
  flex-wrap: wrap;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--sub);
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.d-progress { background: #1d4ed8; }
.d-pending { background: #d97706; }
.d-arrive { background: #eab308; }
.d-suspend { background: #dc2626; }
</style>
