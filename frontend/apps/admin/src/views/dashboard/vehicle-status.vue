<template>
  <div class="status-page">
    <div class="page-head">
      <div class="head-text">
        <h2>车辆状态</h2>
        <p>以车辆为中心：在干什么活、持续多久、最新签到位置</p>
      </div>
      <status-date-bar v-model="date" @change="load" />
    </div>

    <div class="stat-row">
      <div class="stat-item">
        <span class="num">{{ board?.vehicles.length ?? 0 }}</span>
        <span class="label">车辆总数</span>
      </div>
      <div class="stat-item">
        <span class="num active">{{ workingCount }}</span>
        <span class="label">作业中</span>
      </div>
      <div class="stat-item">
        <span class="num">{{ idleCount }}</span>
        <span class="label">空闲</span>
      </div>
      <div class="stat-item">
        <span class="num">{{ otherCount }}</span>
        <span class="label">第三方 / 维保 / 停用</span>
      </div>
    </div>

    <div v-loading="loading" class="vehicle-grid">
      <div
        v-for="v in board?.vehicles ?? []"
        :key="v.vehicleId"
        class="vehicle-card"
        :class="{ active: !!v.currentOrder }"
      >
        <div class="vehicle-head">
          <div class="plate-badge">{{ v.plateNo }}</div>
          <div class="vehicle-info">
            <div class="vehicle-name">
              {{ v.plateNo }}
              <span class="spec">{{ vehicleSpec(v) }}</span>
            </div>
            <div class="vehicle-sub">{{ v.orders.length }} 单 · 当日</div>
          </div>
          <status-tag kind="vehicle" :status="v.status" />
        </div>

        <div v-if="v.currentOrder" class="current-block">
          <div class="current-label">正在干</div>
          <div class="current-title">{{ v.currentOrder.taskName }}</div>
          <div class="current-meta">
            <span>{{ v.currentOrder.customerName || '-' }}</span>
            <span>{{ v.currentOrder.staffName || '未派司机' }}</span>
            <span class="dur">{{ fmtDurationLabel(v.currentOrder) }}</span>
          </div>
          <div class="current-address">{{ v.currentOrder.address }}</div>
        </div>
        <div v-else class="idle-block">
          {{ v.status === 'IDLE' ? '当前空闲，等待派单' : '当前未执行工单' }}
        </div>

        <div class="checkin-block" :class="{ empty: !v.latestCheckIn }">
          <div class="checkin-label">最新签到位置</div>
          <template v-if="v.latestCheckIn">
            <div class="checkin-address">
              {{ hasCheckInCoord(v) ? (checkInAddresses[v.vehicleId] || '地址解析中...') : '签到未记录坐标' }}
            </div>
            <div class="checkin-meta">
              {{ fmtTime(v.latestCheckIn.checkedAt) }}
              <span v-if="v.latestCheckIn.distanceMeters != null">
                · 距工单点位 {{ v.latestCheckIn.distanceMeters }} 米
              </span>
            </div>
          </template>
          <div v-else class="checkin-empty">暂无签到记录</div>
        </div>

        <div v-if="v.orders.length" class="orders-block">
          <div class="orders-title">当日工单</div>
          <div
            v-for="o in v.orders"
            :key="o.id"
            class="order-line"
            @click="goOrder(o.id)"
          >
            <span class="status-dot" :style="{ background: colorOf(o.status) }" />
            <span class="time">{{ fmtClock(o.expectedStartAt) }}</span>
            <span class="task">{{ o.taskName }}</span>
            <span class="staff">{{ o.staffName || '-' }}</span>
            <span class="dur">{{ fmtOrderDuration(o) }}</span>
          </div>
          <day-timeline :orders="v.orders" :date="date" @select="onSelectOrder" />
        </div>
      </div>

      <el-empty
        v-if="!loading && !(board?.vehicles.length)"
        description="暂无车辆数据"
        :image-size="70"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchStatusBoard, type StatusBoard, type StatusBoardOrder, type StatusBoardVehicle } from '@crane/shared/api/stat'
import { amapRegeo } from '@crane/shared/api/map'
import StatusDateBar from '../../components/StatusDateBar.vue'
import DayTimeline from '../../components/DayTimeline.vue'
import StatusTag from '../../components/StatusTag.vue'
import { fmtTime } from '../../utils/format'
import { ORDER_STATUS_COLOR, fmtClock, fmtDurationLabel, fmtOrderDuration, todayStr } from '../../utils/status'

const router = useRouter()
const date = ref(todayStr())
const loading = ref(false)
const board = ref<StatusBoard | null>(null)
const checkInAddresses = ref<Record<number, string>>({})
let loadSeq = 0

const workingCount = computed(
  () => (board.value?.vehicles ?? []).filter((v) => v.status === 'BUSY').length
)
const idleCount = computed(
  () => (board.value?.vehicles ?? []).filter((v) => v.status === 'IDLE').length
)
const otherCount = computed(
  () => (board.value?.vehicles ?? []).filter((v) => ['BUSY_THIRD', 'MAINTENANCE', 'DISABLED'].includes(v.status)).length
)

async function load() {
  const seq = ++loadSeq
  loading.value = true
  checkInAddresses.value = {}
  try {
    const data = await fetchStatusBoard(date.value)
    if (seq !== loadSeq) return
    board.value = data
    resolveCheckInAddresses(data, seq)
  } catch (e) {
    if (seq !== loadSeq) return
    ElMessage.error(e instanceof Error ? e.message : '车辆状态加载失败')
    board.value = null
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

/** 最新签到经纬度反查地址，失败时回退展示坐标 */
async function resolveCheckInAddresses(data: StatusBoard, seq: number) {
  const targets = data.vehicles.filter(
    (v) => v.latestCheckIn?.lng != null && v.latestCheckIn?.latitude != null
  )
  await Promise.all(
    targets.map(async (vehicle) => {
      const checkIn = vehicle.latestCheckIn!
      try {
        const result = await amapRegeo(checkIn.lng!, checkIn.latitude!)
        if (seq !== loadSeq) return
        checkInAddresses.value[vehicle.vehicleId] = result.formattedAddress || '已签到'
      } catch {
        if (seq !== loadSeq) return
        checkInAddresses.value[vehicle.vehicleId] = `${checkIn.lng}, ${checkIn.latitude}`
      }
    })
  )
}

function hasCheckInCoord(vehicle: StatusBoardVehicle) {
  return vehicle.latestCheckIn?.lng != null && vehicle.latestCheckIn?.latitude != null
}

function vehicleSpec(vehicle: { type: string; tonnage: number | null; liftHeight: number | null }) {
  if (vehicle.type === 'LIFT') {
    return vehicle.liftHeight != null ? `举升${vehicle.liftHeight}米` : '举升车'
  }
  if (vehicle.type === 'SEMI_TRAILER') return '板车（半挂）'
  if (vehicle.type === 'HEAVY_TRAILER') return '大件托盘车'
  return vehicle.tonnage != null ? `${vehicle.tonnage}吨` : '吊车'
}

function colorOf(status: string) {
  return ORDER_STATUS_COLOR[status] || '#909399'
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
.vehicle-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 12px;
  min-height: 120px;
}
.vehicle-card {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.vehicle-card.active {
  border-color: rgba(64, 158, 255, 0.45);
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.12);
}
.vehicle-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.plate-badge {
  min-width: 56px;
  height: 34px;
  padding: 0 8px;
  border-radius: 6px;
  background: #1f2d3d;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.5px;
  flex: none;
}
.vehicle-info {
  flex: 1;
  min-width: 0;
}
.vehicle-name {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}
.vehicle-name .spec {
  font-size: 12px;
  font-weight: 400;
  color: var(--sub);
  border: 1px solid var(--line);
  border-radius: 4px;
  padding: 0 6px;
}
.vehicle-sub {
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
.checkin-block {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 12px;
  background: rgba(47, 158, 143, 0.05);
}
.checkin-block.empty {
  background: var(--bg);
}
.checkin-label {
  font-size: 11px;
  color: var(--sub);
  font-weight: 600;
}
.checkin-address {
  font-size: 13px;
  margin-top: 3px;
  line-height: 1.5;
}
.checkin-meta {
  font-size: 12px;
  color: var(--sub);
  margin-top: 3px;
}
.checkin-empty {
  font-size: 13px;
  color: var(--sub);
  margin-top: 3px;
}
.orders-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
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
.order-line .staff {
  color: var(--sub);
  flex: none;
}
.order-line .dur {
  color: var(--sub);
  flex: none;
  min-width: 56px;
  text-align: right;
}
@media (max-width: 1200px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
