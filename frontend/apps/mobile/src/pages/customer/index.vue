<script setup lang="ts">
// 客户端首页（T-33）：我的工单——进行中置顶，已完成/已取消折叠分组
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import BrandBar from '../../components/BrandBar.vue'
import OrderCard from '../../components/OrderCard.vue'
import EmptyState from '../../components/EmptyState.vue'
import RoleTabbar from '../../components/RoleTabbar.vue'
import { listOrders } from '../../api/modules/biz'
import type { WorkOrder } from '../../api/types'
import { toastError } from '../../utils'

const ACTIVE = ['PENDING_ACCEPT', 'PENDING_ARRIVE', 'IN_PROGRESS', 'SUSPENDED']
const DONE = ['COMPLETED', 'CANCELLED']

const loading = ref(false)
const orders = ref<WorkOrder[]>([])
const showDone = ref(false)

const activeOrders = computed(() => orders.value.filter((o) => ACTIVE.includes(o.status)))
const doneOrders = computed(() => orders.value.filter((o) => DONE.includes(o.status)))

async function load() {
  loading.value = true
  try {
    // 客户数据范围由后端按登录上下文过滤；拉取近期工单分组展示
    const res = await listOrders({ pageNum: 1, pageSize: 50 })
    orders.value = res.list ?? []
  } catch (e) {
    toastError(e)
  } finally {
    loading.value = false
  }
}

onShow(load)

function goDetail(o: WorkOrder) {
  uni.navigateTo({ url: `/pages/customer/order-detail?id=${o.id}` })
}
</script>

<template>
  <view class="page">
    <BrandBar />
    <scroll-view scroll-y class="content">
      <view v-if="activeOrders.length" class="group-title">进行中</view>
      <view class="list">
        <OrderCard
          v-for="o in activeOrders"
          :key="o.id"
          :order="o"
          status-key="customerVisibleStatus"
          highlight
          @tap="goDetail(o)"
        />
      </view>
      <EmptyState v-if="!loading && !activeOrders.length" text="暂无进行中的工单" />

      <view v-if="doneOrders.length" class="group-title toggle" @tap="showDone = !showDone">
        <text>已完成 / 已取消（{{ doneOrders.length }}）</text>
        <text class="arrow">{{ showDone ? '收起' : '展开' }}</text>
      </view>
      <view v-if="showDone" class="list">
        <OrderCard
          v-for="o in doneOrders"
          :key="o.id"
          :order="o"
          status-key="customerVisibleStatus"
          @tap="goDetail(o)"
        />
      </view>

      <view v-if="loading" class="loading">加载中...</view>
    </scroll-view>

    <RoleTabbar role="customer" current="pages/customer/index" />
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
.group-title {
  font-size: 13px;
  color: var(--sub);
  margin: 8px 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.toggle .arrow {
  color: var(--brand);
}
.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 8px;
}
.loading {
  text-align: center;
  color: var(--sub);
  font-size: 13px;
  padding: 12px 0;
}
</style>
