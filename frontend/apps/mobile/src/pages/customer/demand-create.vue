<script setup lang="ts">
// 客户端需求提报（T-33）：表单提交 → POST /demand/orders
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { createDemandOrder, listDemands } from '../../api/modules/biz'
import type { DemandOrder } from '../../api/types'
import { useAuthStore } from '../../stores/auth'
import { chooseImages, getLocation, toastError, toastOk } from '../../utils'

const auth = useAuthStore()

const contactName = ref('')
const contactPhone = ref('')
const address = ref('')
const coords = ref<{ longitude: number; latitude: number } | null>(null)
const locating = ref(false)
const description = ref('')
const craneTonnage = ref('')
const cargoWeight = ref('')
const expectDate = ref('')
const expectTime = ref('')
const photos = ref<string[]>([])
const parentDemandId = ref<number | undefined>(undefined)

onLoad((opts) => {
  // 「再次提报」：读取驳回单草稿（由需求列表页写入）
  const draftRaw = uni.getStorageSync('demand_draft')
  if (opts?.parentDemandId && draftRaw) {
    const d = draftRaw as DemandOrder
    parentDemandId.value = Number(opts.parentDemandId)
    contactName.value = d.contactName ?? ''
    contactPhone.value = d.contactPhone ?? ''
    address.value = d.address ?? ''
    description.value = d.description ?? ''
    craneTonnage.value = d.craneTonnage != null ? String(d.craneTonnage) : ''
    cargoWeight.value = d.cargoWeight != null ? String(d.cargoWeight) : ''
    uni.removeStorageSync('demand_draft')
  }
})

onShow(() => {
  if (auth.me) {
    contactName.value = contactName.value || auth.me.name || ''
    contactPhone.value = contactPhone.value || auth.me.phone || ''
  }
})

async function locate() {
  locating.value = true
  try {
    coords.value = await getLocation()
    toastOk('已定位')
  } catch {
    toastError(new Error('定位失败，请检查定位权限'))
  } finally {
    locating.value = false
  }
}

async function takePhoto() {
  try {
    const paths = await chooseImages(1)
    photos.value = [...photos.value, ...paths].slice(0, 3)
  } catch {
    // 用户取消
  }
}

async function submit() {
  if (!contactPhone.value.trim()) {
    uni.showToast({ title: '请填写联系电话', icon: 'none' })
    return
  }
  if (!address.value.trim()) {
    uni.showToast({ title: '请填写施工地址', icon: 'none' })
    return
  }
  if (!/^1\d{10}$/.test(contactPhone.value.trim())) {
    uni.showToast({ title: '联系电话须为 11 位手机号', icon: 'none' })
    return
  }
  try {
    uni.showLoading({ title: '提交中' })
    const expectedStartAt = expectDate.value && expectTime.value ? `${expectDate.value}T${expectTime.value}:00` : undefined
    // TODO(二期): 对接文件上传，photos 目前仅本地预览
    await createDemandOrder({
      source: 'CUSTOMER_MP',
      contactName: contactName.value.trim(),
      contactPhone: contactPhone.value.trim(),
      address: address.value.trim(),
      longitude: coords.value?.longitude,
      latitude: coords.value?.latitude,
      description: description.value.trim() || undefined,
      imageUrls: photos.value.length ? ['mock://local'] : undefined,
      craneTonnage: craneTonnage.value ? Number(craneTonnage.value) : undefined,
      cargoWeight: cargoWeight.value ? Number(cargoWeight.value) : undefined,
      expectedStartAt,
      parentDemandId: parentDemandId.value
    })
    uni.hideLoading()
    toastOk('需求已提交，等待复核')
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/customer/demands' })
    }, 800)
  } catch (e) {
    uni.hideLoading()
    toastError(e)
  }
}
</script>

<template>
  <view class="page">
    <view class="form">
      <view class="field">
        <text class="label">联系人</text>
        <input v-model="contactName" class="input" placeholder="请输入联系人姓名" placeholder-class="ph" />
      </view>
      <view class="field">
        <text class="label">联系电话</text>
        <input v-model="contactPhone" class="input" type="number" maxlength="11" placeholder="请输入联系电话" placeholder-class="ph" />
      </view>
      <view class="field col">
        <text class="label">施工地址</text>
        <input v-model="address" class="input" placeholder="请输入详细地址" placeholder-class="ph" />
        <view class="locate-row">
          <view class="locate" @tap="locate">{{ locating ? '定位中...' : '自动定位' }}</view>
          <text v-if="coords" class="located">已定位（{{ coords.longitude.toFixed(4) }}, {{ coords.latitude.toFixed(4) }}）</text>
        </view>
      </view>
      <view class="field col">
        <text class="label">需求描述</text>
        <textarea v-model="description" class="textarea" placeholder="吊装任务内容、现场条件等" :maxlength="500" />
      </view>
      <view class="field half-wrap">
        <view class="half">
          <text class="label">期望吨位(吨)</text>
          <input v-model="craneTonnage" class="input" type="digit" placeholder="如 25" placeholder-class="ph" />
        </view>
        <view class="half">
          <text class="label">货物重量(吨)</text>
          <input v-model="cargoWeight" class="input" type="digit" placeholder="如 8" placeholder-class="ph" />
        </view>
      </view>
      <view class="field half-wrap">
        <view class="half">
          <text class="label">期望开始日期</text>
          <picker mode="date" :value="expectDate" @change="(e: any) => (expectDate = e.detail.value)">
            <view class="picker-value">{{ expectDate || '选择日期' }}</view>
          </picker>
        </view>
        <view class="half">
          <text class="label">期望开始时间</text>
          <picker mode="time" :value="expectTime" @change="(e: any) => (expectTime = e.detail.value)">
            <view class="picker-value">{{ expectTime || '选择时间' }}</view>
          </picker>
        </view>
      </view>
      <view class="field col">
        <text class="label">现场照片</text>
        <view class="photo-row">
          <view v-for="(p, i) in photos" :key="i" class="photo"><image :src="p" mode="aspectFill" /></view>
          <view v-if="photos.length < 3" class="photo add" @tap="takePhoto">拍照</view>
        </view>
      </view>

      <button class="submit" @tap="submit">提交需求</button>
    </view>
  </view>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--bg);
  padding: 16px 16px 96px;
  box-sizing: border-box;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.field {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 10px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
}
.field.col {
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}
.label {
  flex: none;
  color: var(--sub);
  font-size: 13px;
  width: 96px;
}
.field.col .label {
  width: auto;
}
.half-wrap {
  display: flex;
  gap: 12px;
}
.half {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 10px 16px;
}
.half .label {
  width: auto;
}
.input {
  flex: 1;
  font-size: 15px;
  min-height: 24px;
}
.ph {
  color: var(--sub);
}
.textarea {
  width: 100%;
  box-sizing: border-box;
  min-height: 88px;
  font-size: 14px;
}
.locate-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.locate {
  font-size: 13px;
  color: var(--brand);
}
.located {
  font-size: 12px;
  color: var(--success);
}
.picker-value {
  font-size: 15px;
  color: var(--txt);
  min-height: 24px;
}
.photo-row {
  display: flex;
  gap: 8px;
}
.photo {
  width: 64px;
  height: 64px;
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--bg);
}
.photo image {
  width: 100%;
  height: 100%;
}
.photo.add {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--line);
  color: var(--sub);
  font-size: 13px;
}
.submit {
  margin-top: 8px;
  height: 48px;
  line-height: 48px;
  border-radius: var(--radius);
  background: var(--brand);
  color: #fff;
  font-size: 17px;
  font-weight: 600;
}
.submit::after {
  border: none;
}
</style>
