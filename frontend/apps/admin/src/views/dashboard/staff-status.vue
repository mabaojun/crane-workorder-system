<template>
  <div class="status-page">
    <div class="page-head">
      <div class="head-text">
        <h2>人员状态</h2>
        <p>以人员为中心：正在干什么活、用什么车、已经持续多久</p>
      </div>
      <status-date-bar v-model="date" @change="load" />
    </div>

    <div class="stat-row">
      <div class="stat-item">
        <span class="num">{{ board?.staff.length ?? 0 }}</span>
        <span class="label">人员总数</span>
      </div>
      <div class="stat-item">
        <span class="num active">{{ workingCount }}</span>
        <span class="label">当前作业中</span>
      </div>
      <div class="stat-item">
        <span class="num">{{ scheduledCount }}</span>
        <span class="label">当日有工单</span>
      </div>
      <div class="stat-item">
        <span class="num">{{ board?.totalOrders ?? 0 }}</span>
        <span class="label">当日工单</span>
      </div>
    </div>

    <div v-loading="loading" class="staff-grid">
      <div
        v-for="s in board?.staff ?? []"
        :key="s.staffId"
        class="staff-card"
        :class="{ active: !!s.currentOrder }"
      >
        <div class="person-head">
          <div class="avatar">{{ (s.name || '?').slice(0, 1) }}</div>
          <div class="person-info">
            <div class="person-name">
              {{ s.name }}
              <el-tag size="small" effect="plain" type="info">
                {{ STAFF_POSITION_LABEL[s.position] || s.position }}
              </el-tag>
            </div>
            <div class="person-phone">{{ s.phone }}</div>
          </div>
          <el-tag size="small" :type="staffTagType(s.status)" effect="light">
            {{ s.currentOrder ? '作业中' : (STAFF_STATUS_LABEL[s.status] || s.status) }}
          </el-tag>
        </div>

        <div v-if="s.currentOrder" class="current-block">
          <div class="current-label">正在干</div>
          <div class="current-title">{{ s.currentOrder.taskName }}</div>
          <div class="current-meta">
            <span>{{ s.currentOrder.customerName || '-' }}</span>
            <span>{{ vehicleSpec(s.currentOrder) }}</span>
            <span class="dur">{{ fmtDurationLabel(s.currentOrder) }}</span>
          </div>
          <div class="current-address">{{ s.currentOrder.address }}</div>
        </div>
        <div v-else class="idle-block">当前空闲，无进行中工单</div>

        <div v-if="s.orders.length" class="orders-block">
          <div class="orders-title">
            当日工单
            <span>{{ s.orders.length }} 单 · 合计 {{ s.totalMinutes ? fmtDuration(s.totalMinutes) : '—' }}</span>
          </div>
          <div
            v-for="o in s.orders"
            :key="o.id"
            class="order-line"
            @click="goOrder(o.id)"
          >
            <span class="status-dot" :style="{ background: colorOf(o.status) }" />
            <span class="time">{{ fmtClock(o.expectedStartAt) }}</span>
            <span class="task">{{ o.taskName }}</span>
            <span class="plate">{{ o.vehiclePlateNo || '-' }}</span>
            <span class="dur">{{ fmtOrderDuration(o) }}</span>
          </div>
          <day-timeline :orders="s.orders" :date="date" @select="onSelectOrder" />
        </div>
        <div v-else class="no-order">当日无排班工单</div>
      </div>

      <el-empty
        v-if="!loading && !(board?.staff.length)"
        description="暂无人员数据"
        :image-size="70"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  fetchStatusBoard,
  type StatusBoard,
  type StatusBoardOrder
} from '@crane/shared/api/stat'
import StatusDateBar from '../../components/StatusDateBar.vue'
import DayTimeline from '../../components/DayTimeline.vue'
import {
  ORDER_STATUS_COLOR,
  STAFF_POSITION_LABEL,
  STAFF_STATUS_LABEL,
  fmtClock,
  fmtDuration,
  fmtDurationLabel,
  fmtOrderDuration,
  todayStr
} from '../../utils/status'

const router = useRouter()
const date = ref(todayStr())
const loading = ref(false)
const board = ref<StatusBoard | null>(null)

const workingCount = computed(
  () => (board.value?.staff ?? []).filter((s) => !!s.currentOrder || s.status === 'BUSY').length
)
const scheduledCount = computed(
  () => (board.value?.staff ?? []).filter((s) => s.orders.length > 0).length
)

async function load() {
  loading.value = true
  try {
    board.value = await fetchStatusBoard(date.value)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '人员状态加载失败')
    board.value = null
  } finally {
    loading.value = false
  }
}

function staffTagType(status: string) {
  if (status === 'BUSY') return 'success'
  if (status === 'AWAY') return 'warning'
  if (status === 'RESIGNED') return 'info'
  return 'info'
}

function colorOf(status: string) {
  return ORDER_STATUS_COLOR[status] || '#909399'
}

function vehicleSpec(order: StatusBoardOrder) {
  if (!order.vehiclePlateNo) return '未派车'
  const spec = order.vehicleType === 'LIFT'
    ? order.liftHeight != null ? `举升${order.liftHeight}米` : '举升车'
    : order.vehicleType === 'SEMI_TRAILER'
      ? '板车（半挂）'
      : order.vehicleType === 'HEAVY_TRAILER'
        ? '大件托盘车'
        : order.tonnage != null ? `${order.tonnage}吨` : '吊车'
  return `${order.vehiclePlateNo} · ${spec}`
}

function goOrder(id: number) {
  router.push(`/workorder/detail/${id}`)
}

function onSelectOrder(order: StatusBoardOrder) {
  goOrder(order.id)
}

onMounted(load)
</script>

<style scoped>
.status-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.page-head {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 16px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.head-text h2 {
  margin: 0 0 4px;
  font-size: 20px;
}
.head-text p {
  margin: 0;
  color: var(--sub);
  font-size: 13px;
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.stat-item {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stat-item .num {
  font-size: 24px;
  font-weight: 700;
  color: var(--txt);
  font-variant-numeric: tabular-nums;
}
.stat-item .num.active {
  color: var(--brand);
}
.stat-item .label {
  font-size: 12px;
  color: var(--sub);
}
.staff-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 12px;
  min-height: 120px;
}
.staff-card {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.staff-card.active {
  border-color: rgba(64, 158, 255, 0.45);
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.12);
}
.person-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  flex: none;
}
.person-info {
  flex: 1;
  min-width: 0;
}
.person-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 15px;
}
.person-phone {
  font-size: 12px;
  color: var(--sub);
  margin-top: 2px;
}
.current-block {
  background: rgba(64, 158, 255, 0.08);
  border: 1px solid rgba(64, 158, 255, 0.2);
  border-radius: 8px;
  padding: 10px 12px;
}
.current-label {
  font-size: 11px;
  color: var(--brand);
  font-weight: 600;
}
.current-title {
  font-size: 15px;
  font-weight: 600;
  margin: 2px 0 4px;
}
.current-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--sub);
}
.current-meta .dur {
  color: var(--brand);
  font-weight: 600;
}
.current-address {
  font-size: 12px;
  color: var(--sub);
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.idle-block {
  background: var(--bg);
  border: 1px dashed var(--line);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  color: var(--sub);
}
.orders-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
}
.orders-title span {
  font-weight: 400;
  font-size: 12px;
  color: var(--sub);
}
.order-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 5px 0;
  border-bottom: 1px dashed var(--line);
  cursor: pointer;
}
.order-line:hover .task {
  color: var(--brand);
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}
.order-line .time {
  color: var(--sub);
  font-variant-numeric: tabular-nums;
  flex: none;
}
.order-line .task {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.order-line .plate {
  color: var(--sub);
  flex: none;
}
.order-line .dur {
  color: var(--sub);
  flex: none;
  min-width: 56px;
  text-align: right;
}
.no-order {
  font-size: 12px;
  color: var(--sub);
}
@media (max-width: 1200px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
