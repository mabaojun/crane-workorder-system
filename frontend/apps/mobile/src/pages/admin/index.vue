<script setup lang="ts">
// 管理员移动办公台：待办统计 + 快捷审批入口 + 实时报警 + 进行中工单
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import RoleTabbar from '../../components/RoleTabbar.vue'
import StatusTag from '../../components/StatusTag.vue'
import { useAuthStore } from '../../stores/auth'
import { fmtDateTime, toastError } from '../../utils'
import { pageDemands } from '@crane/shared/api/demand'
import { pageExceptions } from '@crane/shared/api/exception'
import { pagePendingRedispatch } from '@crane/shared/api/dispatch'
import { pageWorkOrders, type WorkOrder } from '@crane/shared/api/workorder'
import { fetchStatAlarms, type StatAlarmItem } from '@crane/shared/api/stat'

const auth = useAuthStore()
const loading = ref(false)
const errors = ref<string[]>([])
const alarms = ref<StatAlarmItem[]>([])
const activeOrders = ref<WorkOrder[]>([])

const stats = reactive({
  demandPending: 0,
  demandReviewing: 0,
  exceptionPending: 0,
  redispatch: 0,
  inProgress: 0
})

const totalPending = computed(
  () => stats.demandPending + stats.exceptionPending + stats.redispatch
)

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜间值守'
  if (h < 12) return '早上好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const today = computed(() =>
  new Date().toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', weekday: 'long' })
)

async function safe<T>(label: string, task: Promise<T>, assign: (value: T) => void) {
  try {
    assign(await task)
  } catch (e) {
    errors.value.push(`${label}：${e instanceof Error ? e.message : '加载失败'}`)
  }
}

async function load() {
  loading.value = true
  errors.value = []
  await Promise.all([
    safe('待审需求', pageDemands({ status: 'PENDING', pageNum: 1, pageSize: 1 }), (r) => {
      stats.demandPending = r.total ?? 0
    }),
    safe('处理中需求', pageDemands({ status: 'REVIEWING', pageNum: 1, pageSize: 1 }), (r) => {
      stats.demandReviewing = r.total ?? 0
    }),
    safe('待批异常', pageExceptions({ status: 'PENDING', pageNum: 1, pageSize: 1 }), (r) => {
      stats.exceptionPending = r.total ?? 0
    }),
    safe('待重新派单', pagePendingRedispatch({ pageNum: 1, pageSize: 1 }), (r) => {
      stats.redispatch = r.total ?? 0
    }),
    safe('进行中工单', pageWorkOrders({ status: 'IN_PROGRESS', pageNum: 1, pageSize: 1 }), (r) => {
      stats.inProgress = r.total ?? 0
    }),
    safe('报警信息', fetchStatAlarms(), (r) => {
      alarms.value = (r.list ?? []).slice(0, 5)
    }),
    safe('进行中工单', pageWorkOrders({ status: 'IN_PROGRESS', pageNum: 1, pageSize: 5 }), (r) => {
      activeOrders.value = r.list ?? []
    })
  ])
  loading.value = false
}

onShow(async () => {
  if (auth.token && !auth.me) {
    try {
      await auth.loadMe()
    } catch (e) {
      toastError(e)
    }
  }
  load()
})

function go(path: string) {
  uni.navigateTo({ url: path })
}

function goOrder(id: number | null) {
  if (!id) return
  uni.navigateTo({ url: `/pages/admin/order-detail?id=${id}` })
}

function alarmLevel(level: string) {
  return level === 'HIGH' ? '高' : '中'
}
</script>

<template>
  <view class="adm-page">
    <view class="adm-hero">
      <text class="adm-hero-kicker">MOBILE COMMAND · ADMIN</text>
      <text class="adm-hero-title">
        {{ greeting }}，{{ auth.me?.name || '管理员' }}
      </text>
      <text class="adm-hero-sub">
        <text v-if="totalPending" class="hl">{{ totalPending }}</text>
        <text v-if="totalPending"> 项待处理 · </text>
        <text v-else>当前无待办 · </text>{{ today }}
      </text>
      <view class="adm-hero-badge">移动办公 · 实时审批</view>
    </view>

    <!-- 下工单主入口：最常用操作，语音说单作为辅助入口弱化展示 -->
    <view class="create-entry" @tap="go('/pages/admin/order-create')">
      <view class="create-icon">
        <uni-icons type="compose" size="26" color="#ffffff" />
      </view>
      <view class="create-copy">
        <text class="create-title">新建工单</text>
        <text class="create-desc">选客户、司机与车辆，取点后直接下发</text>
      </view>
      <view class="create-voice" @tap.stop="go('/pages/admin/voice-create')">
        <uni-icons type="mic" size="14" color="#a5f3fc" />
        <text>语音说单</text>
      </view>
    </view>

    <view v-if="errors.length" class="adm-error">
      {{ errors.join('；') }}
    </view>

    <!-- 待办总览：卡片即入口，点击直接进入对应处理页面 -->
    <view class="adm-grid-2">
      <view class="adm-stat clickable" style="--accent:#f59e0b" @tap="go('/pages/admin/demands')">
        <uni-icons class="stat-go" type="right" size="14" color="#cbd5e1" />
        <text class="adm-stat-num">{{ stats.demandPending }}</text>
        <text class="adm-stat-label">待审需求</text>
        <text class="adm-stat-note">处理中 {{ stats.demandReviewing }} 单 · 去审批</text>
      </view>
      <view class="adm-stat clickable" style="--accent:#ef4444" @tap="go('/pages/admin/exceptions')">
        <uni-icons class="stat-go" type="right" size="14" color="#cbd5e1" />
        <text class="adm-stat-num">{{ stats.exceptionPending }}</text>
        <text class="adm-stat-label">待批异常</text>
        <text class="adm-stat-note">司机上报待批复 · 去处理</text>
      </view>
      <view class="adm-stat clickable" style="--accent:#0e7490" @tap="go('/pages/admin/orders?status=PENDING_ACCEPT')">
        <uni-icons class="stat-go" type="right" size="14" color="#cbd5e1" />
        <text class="adm-stat-num">{{ stats.redispatch }}</text>
        <text class="adm-stat-label">待重新派单</text>
        <text class="adm-stat-note">拒单 / 退单待处理 · 去派单</text>
      </view>
      <view class="adm-stat clickable" style="--accent:#22c55e" @tap="go('/pages/admin/orders?status=IN_PROGRESS')">
        <uni-icons class="stat-go" type="right" size="14" color="#cbd5e1" />
        <text class="adm-stat-num">{{ stats.inProgress }}</text>
        <text class="adm-stat-label">进行中工单</text>
        <text class="adm-stat-note">现场施工实时跟踪 · 查看</text>
      </view>
    </view>

    <view class="adm-section">
      <view class="adm-section-head">
        <text class="adm-section-title">实时报警</text>
        <text class="adm-section-link" @tap="go('/pages/admin/orders')">工单列表</text>
      </view>
      <view v-if="alarms.length" class="adm-list">
        <view
          v-for="a in alarms"
          :key="a.alarmNo"
          class="adm-item"
          @tap="goOrder(a.orderId)"
        >
          <view class="adm-item-head">
            <text class="adm-badge" :style="{ background: a.level === 'HIGH' ? '#dc2626' : '#f59e0b' }">
              {{ alarmLevel(a.level) }}
            </text>
            <text class="adm-item-no">{{ a.plateNo || a.orderNo || '系统提醒' }}</text>
            <text class="adm-time">{{ fmtDateTime(a.triggeredAt) }}</text>
          </view>
          <text class="adm-item-title">{{ a.summary }}</text>
          <view class="adm-item-meta">
            <text v-if="a.staffName">司机 {{ a.staffName }}</text>
            <text v-if="a.durationMinutes != null">持续 {{ a.durationMinutes }} 分钟</text>
          </view>
        </view>
      </view>
      <view v-else class="adm-empty">{{ loading ? '加载中...' : '暂无报警，运行正常' }}</view>
    </view>

    <view class="adm-section">
      <view class="adm-section-head">
        <text class="adm-section-title">进行中工单</text>
        <text class="adm-section-link" @tap="go('/pages/admin/orders?status=IN_PROGRESS')">查看全部</text>
      </view>
      <view v-if="activeOrders.length" class="adm-list">
        <view v-for="o in activeOrders" :key="o.id" class="adm-item" @tap="goOrder(o.id)">
          <view class="adm-item-head">
            <text class="adm-item-no">{{ o.orderNo }}</text>
            <StatusTag :status="o.status" />
          </view>
          <text class="adm-item-title">{{ o.taskName }}</text>
          <view class="adm-item-meta">
            <text>{{ o.customerName || '未知客户' }}</text>
            <text>{{ o.staffName || '未派司机' }}</text>
            <text>{{ o.vehiclePlateNo || '未派车' }}</text>
          </view>
          <view class="adm-item-foot">
            <text class="adm-time">计划 {{ fmtDateTime(o.expectedStartAt) }}</text>
            <text class="adm-section-link">查看详情</text>
          </view>
        </view>
      </view>
      <view v-else class="adm-empty">{{ loading ? '加载中...' : '暂无进行中工单' }}</view>
    </view>

    <RoleTabbar role="admin" current="pages/admin/index" />
  </view>
</template>

<style scoped>
/* 下工单主入口：强化主操作 */
.create-entry {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 18px;
  background: linear-gradient(135deg, #0b1f3a 0%, #123a6b 60%, #0e7490 100%);
  box-shadow: 0 10px 24px rgba(11, 31, 58, 0.22);
}
.create-entry::after {
  content: '';
  position: absolute;
  right: -40px;
  top: -54px;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.32), transparent 70%);
  pointer-events: none;
}
.create-icon {
  flex: none;
  width: 46px;
  height: 46px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(103, 232, 249, 0.14);
  border: 1px solid rgba(103, 232, 249, 0.4);
}
.create-copy {
  flex: 1;
  min-width: 0;
}
.create-title {
  display: block;
  font-size: 17px;
  font-weight: 700;
  color: #fff;
}
.create-desc {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.66);
}
.create-voice {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(125, 211, 252, 0.35);
  background: rgba(255, 255, 255, 0.08);
  color: #a5f3fc;
  font-size: 12px;
}
/* 待办卡片点击态与跳转箭头 */
.adm-stat.clickable:active {
  border-color: #7dd3fc;
  background: #f0f9ff;
}
.stat-go {
  position: absolute;
  right: 10px;
  top: 10px;
}
</style>
