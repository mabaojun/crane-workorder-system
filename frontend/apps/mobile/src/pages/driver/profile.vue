<script setup lang="ts">
// 司机端我的页（T-31）：基础信息 + 常用设备状态维护 + 登出
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import SectionCard from '../../components/SectionCard.vue'
import RoleTabbar from '../../components/RoleTabbar.vue'
import { useAuthStore } from '../../stores/auth'
import { toastError } from '../../utils'
import { VEHICLE_STATUS_LABEL } from '@crane/shared'
import {
  listMyVehicles,
  driverChangeVehicleStatus,
  vehicleSpecLabel,
  type Vehicle
} from '@crane/shared/api/staff'

const auth = useAuthStore()
const name = computed(() => auth.me?.name ?? '-')
const phone = computed(() => auth.me?.phone ?? '-')
const staffNo = computed(() => (auth.me?.staffId ? String(auth.me.staffId) : '未绑定'))
const roleLabel = ref('司机')

/** 司机可手动切换的目标状态（与后端白名单一致） */
const DRIVER_STATUS_OPTIONS = [
  { value: 'IDLE', label: '空闲' },
  { value: 'BUSY_THIRD', label: '第三方外出' },
  { value: 'MAINTENANCE', label: '维保' }
]

const vehicles = ref<Vehicle[]>([])
const vehiclesLoading = ref(false)
const switchingId = ref<number | null>(null)

onShow(async () => {
  if (auth.token && !auth.me) {
    try {
      await auth.loadMe()
    } catch (e) {
      toastError(e)
    }
  }
  loadVehicles()
})

async function loadVehicles() {
  if (!auth.token) return
  vehiclesLoading.value = true
  try {
    vehicles.value = await listMyVehicles()
  } catch (e) {
    toastError(e)
  } finally {
    vehiclesLoading.value = false
  }
}

/** 状态标签配色：空闲绿 / 作业中蓝 / 第三方橙 / 维保灰 / 停用灰 */
function statusClass(status: string): string {
  switch (status) {
    case 'IDLE': return 'st-idle'
    case 'BUSY': return 'st-busy'
    case 'BUSY_THIRD': return 'st-third'
    default: return 'st-off'
  }
}

function onSwitch(vehicle: Vehicle, status: string) {
  if (vehicle.status === status) return
  const label = DRIVER_STATUS_OPTIONS.find((o) => o.value === status)?.label ?? status
  uni.showModal({
    title: '变更设备状态',
    content: `将「${vehicle.plateNo}」变更为「${label}」？`,
    success: async (r) => {
      if (!r.confirm) return
      switchingId.value = vehicle.id
      try {
        await driverChangeVehicleStatus(vehicle.id, status)
        uni.showToast({ title: '已变更', icon: 'success' })
        await loadVehicles()
      } catch (e) {
        toastError(e)
      } finally {
        switchingId.value = null
      }
    }
  })
}

async function onLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确认退出当前账号？',
    success: async (r) => {
      if (!r.confirm) return
      await auth.doLogout()
      uni.reLaunch({ url: '/pages/login/index' })
    }
  })
}
</script>

<template>
  <view class="page">
    <view class="user-head">
      <view class="avatar">{{ name.slice(0, 1) }}</view>
      <view class="meta">
        <text class="name">{{ name }}</text>
        <text class="phone">{{ phone }}</text>
      </view>
      <text class="role">{{ roleLabel }}</text>
    </view>

    <SectionCard title="账号信息">
      <view class="row"><text class="k">姓名</text><text>{{ name }}</text></view>
      <view class="row"><text class="k">手机号</text><text>{{ phone }}</text></view>
      <view class="row"><text class="k">工号</text><text>{{ staffNo }}</text></view>
    </SectionCard>

    <SectionCard title="常用设备">
      <view v-if="vehiclesLoading" class="hint">加载中…</view>
      <view v-else-if="vehicles.length === 0" class="hint">暂无绑定设备，请联系管理员在后台绑定</view>
      <view v-for="v in vehicles" :key="v.id" class="vehicle-item">
        <view class="vehicle-head">
          <text class="plate">{{ v.plateNo }}</text>
          <text class="spec">{{ vehicleSpecLabel(v) }}</text>
          <text class="status" :class="statusClass(v.status)">
            {{ VEHICLE_STATUS_LABEL[v.status as keyof typeof VEHICLE_STATUS_LABEL] || v.status }}
          </text>
        </view>
        <view v-if="v.model" class="vehicle-model">{{ v.model }}</view>
        <view class="vehicle-actions">
          <button
            v-for="opt in DRIVER_STATUS_OPTIONS"
            :key="opt.value"
            class="st-btn"
            :class="{ active: v.status === opt.value }"
            :disabled="v.status === 'BUSY' || v.status === 'DISABLED' || switchingId === v.id"
            @click="onSwitch(v, opt.value)"
          >{{ opt.label }}</button>
        </view>
        <view v-if="v.status === 'BUSY'" class="hint">作业中，状态由工单自动更新</view>
        <view v-else-if="v.status === 'DISABLED'" class="hint">已停用，请联系管理员</view>
      </view>
    </SectionCard>

    <button class="logout" @click="onLogout">退出登录</button>

    <RoleTabbar role="driver" current="pages/driver/profile" />
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
  padding: 16px 16px calc(96px + env(safe-area-inset-bottom));
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.user-head {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 14px;
  background: linear-gradient(135deg, #0b1f3a 0%, #123a6b 62%, #0e7490 100%);
  border: 1px solid rgba(103, 232, 249, 0.18);
  border-radius: var(--radius-lg);
  box-shadow: 0 8px 22px rgba(11, 31, 58, 0.2);
  padding: 18px;
}
.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(165, 243, 252, 0.16);
  border: 1px solid rgba(103, 232, 249, 0.55);
  color: #a5f3fc;
  font-size: 22px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.name {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
}
.phone {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
  font-variant-numeric: tabular-nums;
}
.role {
  font-size: 12px;
  font-weight: 600;
  color: #0b1f3a;
  background: #a5f3fc;
  border-radius: 999px;
  padding: 3px 10px;
}
.row {
  display: flex;
  gap: 8px;
  font-size: 15px;
  margin-bottom: 9px;
}
.k {
  color: var(--sub);
  font-size: 14px;
  flex: none;
  width: 72px;
}
.logout {
  width: 100%;
  display: block;
  height: 50px;
  line-height: 50px;
  border-radius: var(--radius);
  background: #fff;
  color: var(--danger);
  font-size: 17px;
  font-weight: 600;
  border: 1px solid var(--danger);
  box-sizing: border-box;
}
.logout::after {
  border: none;
}
.hint {
  font-size: 13px;
  color: var(--sub);
  margin-top: 6px;
}
.vehicle-item {
  border-top: 1px solid var(--line);
  padding-top: 14px;
  margin-top: 14px;
}
.vehicle-item:first-of-type {
  border-top: none;
  padding-top: 0;
  margin-top: 0;
}
.vehicle-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.plate {
  font-size: 18px;
  font-weight: 700;
}
.spec {
  font-size: 13px;
  color: var(--sub);
}
.status {
  margin-left: auto;
  font-size: 13px;
  font-weight: 600;
  border-radius: 6px;
  padding: 2px 10px;
}
.st-idle {
  color: var(--success, #07c160);
  background: rgba(7, 193, 96, 0.12);
}
.st-busy {
  color: var(--brand);
  background: var(--brand-weak);
}
.st-third {
  color: var(--warning, #ed6a0c);
  background: rgba(237, 106, 12, 0.12);
}
.st-off {
  color: var(--sub);
  background: rgba(144, 147, 153, 0.12);
}
.vehicle-model {
  font-size: 13px;
  color: var(--sub);
  margin-top: 4px;
}
.vehicle-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.st-btn {
  flex: 1;
  min-height: 46px;
  line-height: 46px;
  font-size: 15px;
  font-weight: 600;
  border-radius: var(--radius);
  background: var(--panel);
  border: 1px solid var(--line);
  color: var(--txt);
  padding: 0;
  box-sizing: border-box;
}
.st-btn.active {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--brand-weak);
}
.st-btn[disabled] {
  opacity: 0.4;
}
.st-btn::after {
  border: none;
}
</style>
