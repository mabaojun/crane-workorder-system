<script setup lang="ts">
// 司机端工单列表（T-31 重构）：分页列表，点击卡片统一跳转专属操作页
import { computed, ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import OrderCard from '../../components/OrderCard.vue'
import EmptyState from '../../components/EmptyState.vue'
import RoleTabbar from '../../components/RoleTabbar.vue'
import { listOrders } from '../../api/modules/biz'
import type { WorkOrder } from '../../api/types'
import { toastError } from '../../utils'

const list = ref<WorkOrder[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const loading = ref(false)

const hasMore = computed(() => list.value.length < total.value)

async function load(reset = false) {
  if (loading.value) return
  loading.value = true
  try {
    if (reset) {
      pageNum.value = 1
    }
    const res = await listOrders({ pageNum: pageNum.value, pageSize })
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

/** 统一跳转专属操作页：所有工单操作在该页完成 */
function goDetail(o: WorkOrder) {
  uni.navigateTo({ url: `/pages/driver/order-detail?id=${o.id}` })
}
</script>

<template>
  <view class="page">
    <view class="content">
      <EmptyState v-if="!loading && !list.length" text="暂无工单" />
      <view v-else class="list">
        <OrderCard
          v-for="o in list"
          :key="o.id"
          :order="o"
          status-key="status"
          @tap="goDetail(o)"
        />
        <view v-if="loading" class="loading">加载中...</view>
        <view v-else-if="!hasMore && list.length" class="loading">没有更多了</view>
      </view>
    </view>

    <RoleTabbar role="driver" current="pages/driver/orders" />
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
  display: flex;
  flex-direction: column;
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
.loading {
  text-align: center;
  color: var(--sub);
  font-size: 13px;
  padding: 12px 0;
}
</style>
