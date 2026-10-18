<script setup lang="ts">
// 司机端工单列表（T-31）：分页列表 + 点卡片展示详情抽屉
import { computed, ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import OrderCard from '../../components/OrderCard.vue'
import EmptyState from '../../components/EmptyState.vue'
import StatusTag from '../../components/StatusTag.vue'
import RoleTabbar from '../../components/RoleTabbar.vue'
import { listOrders } from '../../api/modules/biz'
import type { WorkOrder } from '../../api/types'
import { fmtDateTime, toastError } from '../../utils'
import { openNavigation } from '../../utils/navigation'

const list = ref<WorkOrder[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const loading = ref(false)
const detail = ref<WorkOrder | null>(null)

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

function openDetail(o: WorkOrder) {
  detail.value = o
}

function closeDetail() {
  detail.value = null
}

/** 详情抽屉一键导航 */
function goNavigate() {
  const o = detail.value
  if (!o) return
  if (o.longitude == null || o.latitude == null) {
    uni.showToast({ title: '该工单无位置坐标', icon: 'none' })
    return
  }
  openNavigation({ lng: o.longitude, lat: o.latitude, name: o.taskName || o.orderNo, address: o.address })
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
          @tap="openDetail(o)"
        />
        <view v-if="loading" class="loading">加载中...</view>
        <view v-else-if="!hasMore && list.length" class="loading">没有更多了</view>
      </view>
    </view>

    <!-- 详情抽屉 -->
    <view v-if="detail" class="mask" @tap="closeDetail">
      <view class="drawer" @tap.stop>
        <view class="head">
          <text class="no">{{ detail.orderNo }}</text>
          <StatusTag :status="detail.status" />
          <view class="close" @tap="closeDetail">关闭</view>
        </view>
        <scroll-view scroll-y class="body">
          <view class="title">{{ detail.taskName || '吊装任务' }}</view>
          <view class="row"><text class="k">客户</text><text>{{ detail.customerName || '-' }}</text></view>
          <view class="row"><text class="k">司机</text><text>{{ detail.staffName || '-' }}</text></view>
          <view class="row"><text class="k">车牌</text><text>{{ detail.vehiclePlateNo || '-' }}</text></view>
          <view class="row"><text class="k">地址</text><text>{{ detail.address || '-' }}</text></view>
          <view class="row">
            <text class="k">导航</text>
            <view v-if="detail.longitude != null && detail.latitude != null" class="nav-link" @tap="goNavigate">一键导航</view>
            <text v-else class="sub-text">无位置坐标</text>
          </view>
          <view class="row"><text class="k">计划开始</text><text>{{ fmtDateTime(detail.expectedStartAt) }}</text></view>
          <view class="row"><text class="k">接单时间</text><text>{{ fmtDateTime(detail.acceptedAt) }}</text></view>
          <view class="row"><text class="k">开工时间</text><text>{{ fmtDateTime(detail.startedAt) }}</text></view>
          <view class="row"><text class="k">完成时间</text><text>{{ fmtDateTime(detail.completedAt) }}</text></view>
          <view class="row"><text class="k">吊车吨位</text><text>{{ detail.craneTonnage ?? '-' }}</text></view>
          <view class="row"><text class="k">货物</text><text>{{ detail.cargoName || '-' }}{{ detail.cargoWeight ? ' / ' + detail.cargoWeight + ' 吨' : '' }}</text></view>
        </scroll-view>
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
.mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}
.drawer {
  width: 100%;
  background: var(--panel);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  display: flex;
  flex-direction: column;
  max-height: 80vh;
}
.head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
}
.no {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.close {
  font-size: 13px;
  color: var(--sub);
}
.body {
  padding: 16px;
  max-height: 64vh;
  box-sizing: border-box;
}
.title {
  font-size: 17px;
  font-weight: 600;
  margin-bottom: 12px;
}
.row {
  display: flex;
  gap: 8px;
  font-size: 14px;
  margin-bottom: 8px;
}
.k {
  color: var(--sub);
  flex: none;
  width: 64px;
}
.nav-link {
  color: var(--brand);
  font-size: 14px;
  text-decoration: underline;
}
.sub-text {
  color: var(--sub);
  font-size: 13px;
}
</style>
