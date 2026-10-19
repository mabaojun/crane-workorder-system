<script setup lang="ts">
// 一句话下工单：语音/文字输入 → 大模型或本地规则拆解 → 数据库匹配 → 回填新建工单或需求转工单表单
import { computed, ref } from 'vue'
import { onUnload } from '@dcloudio/uni-app'
import dayjs from 'dayjs'
import { parseOrderByText, type VoiceOrderParseResult } from '@crane/shared/api/ai'
import { toastError, toastOk } from '../../utils'

const sentence = ref('')
const inputFocus = ref(false)
const parsing = ref(false)
const result = ref<VoiceOrderParseResult | null>(null)
const recording = ref(false)

const EXAMPLES = [
  '明天上午八点，给肥城昌盛特种石墨有限公司在石横镇派25吨吊车，司机张超，车牌鲁AY8679，货物约5吨，备注带顶笼',
  '把需求单 XQ202610180018 转成工单，司机张超，车鲁AY8679，明天早上八点，25吨吊车',
  '今天下午三点，给E2E测试客户在澳海高楼部安排25吨吊车，司机马保俊，货物5吨'
]

const canParse = computed(() => sentence.value.trim().length >= 4)

const speechSupported = computed(() => {
  if (typeof window === 'undefined') return false
  const w = window as any
  return !!(w.SpeechRecognition || w.webkitSpeechRecognition)
})

let recognition: any = null
let baseText = ''

function useExample(text: string) {
  sentence.value = text
  result.value = null
}

function focusInput() {
  inputFocus.value = true
  setTimeout(() => {
    inputFocus.value = false
  }, 400)
}

function toggleVoice() {
  if (!speechSupported.value) {
    focusInput()
    uni.showToast({ title: '请点击输入框，使用键盘语音输入', icon: 'none' })
    return
  }
  if (recording.value) {
    stopVoice()
    return
  }
  startVoice()
}

function startVoice() {
  const w = window as any
  const SpeechRecognition = w.SpeechRecognition || w.webkitSpeechRecognition
  try {
    recognition = new SpeechRecognition()
    recognition.lang = 'zh-CN'
    recognition.continuous = true
    recognition.interimResults = true
    baseText = sentence.value ? sentence.value + '，' : ''
    recognition.onresult = (event: any) => {
      let text = ''
      for (let i = event.resultIndex; i < event.results.length; i++) {
        text += event.results[i][0].transcript
      }
      sentence.value = (baseText + text).slice(0, 500)
      result.value = null
    }
    recognition.onerror = (event: any) => {
      recording.value = false
      if (event?.error === 'not-allowed' || event?.error === 'service-not-allowed') {
        uni.showToast({ title: '麦克风权限未开启', icon: 'none' })
      } else if (event?.error === 'no-speech') {
        uni.showToast({ title: '未识别到语音，请重试', icon: 'none' })
      }
    }
    recognition.onend = () => {
      recording.value = false
    }
    recognition.start()
    recording.value = true
  } catch {
    recording.value = false
    focusInput()
    uni.showToast({ title: '语音启动失败，请使用键盘语音输入', icon: 'none' })
  }
}

function stopVoice() {
  if (recognition) {
    recognition.stop()
  }
  recording.value = false
}

async function parse() {
  if (!canParse.value || parsing.value) return
  parsing.value = true
  result.value = null
  try {
    const res = await parseOrderByText(sentence.value.trim())
    result.value = res
    toastOk(res.parseMode === 'LLM' ? '大模型解析完成' : '本地规则解析完成')
  } catch (e) {
    toastError(e)
  } finally {
    parsing.value = false
  }
}

function clearAll() {
  sentence.value = ''
  result.value = null
  stopVoice()
}

function forceCreate() {
  if (!result.value) return
  result.value.intent = 'CREATE_ORDER'
  result.value.demandId = null
  result.value.demandNo = null
}

function goFill() {
  const r = result.value
  if (!r) return
  if (r.intent === 'DEMAND_CONVERT' && r.demandId) {
    uni.setStorageSync('demand_convert_prefill', {
      demandId: r.demandId,
      staffId: r.staffId,
      staffName: r.staffName,
      vehicleId: r.vehicleId,
      plateNo: r.plateNo,
      planStartAt: r.planStartAt,
      craneTonnage: r.craneTonnage,
      cargoWeight: r.cargoWeight,
      remark: r.remark
    })
    uni.navigateTo({ url: `/pages/admin/demand-detail?id=${r.demandId}` })
    return
  }
  uni.setStorageSync('voice_order_prefill', {
    customerId: r.customerId,
    customerName: r.customerName,
    staffId: r.staffId,
    staffName: r.staffName,
    vehicleId: r.vehicleId,
    plateNo: r.plateNo,
    address: r.address,
    longitude: r.longitude,
    latitude: r.latitude,
    planStartAt: r.planStartAt,
    craneTonnage: r.craneTonnage,
    cargoWeight: r.cargoWeight,
    cargoName: r.cargoName,
    remark: r.remark
  })
  uni.navigateTo({ url: '/pages/admin/order-create' })
}

function fmtPlan(value: string | null) {
  return value && dayjs(value).isValid() ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}

function val(value: string | number | null | undefined) {
  return value === null || value === undefined || value === '' ? '-' : String(value)
}

onUnload(stopVoice)
</script>

<template>
  <view class="adm-page">
    <view class="adm-hero">
      <text class="adm-hero-kicker">VOICE ORDER · AI</text>
      <text class="adm-hero-title">一句话下工单</text>
      <text class="adm-hero-sub">说出客户、时间、地址、吨位、司机和车辆，自动拆解并填表</text>
      <view class="adm-hero-badge">语音转文字 · 大模型拆解 · 档案匹配</view>
    </view>

    <view class="adm-section">
      <view class="voice-head">
        <text class="adm-section-title">口述内容</text>
        <text class="char-count">{{ sentence.length }}/500</text>
      </view>
      <textarea
        v-model="sentence"
        class="voice-textarea"
        :focus="inputFocus"
        :maxlength="500"
        placeholder="例如：明天上午8点，给XX客户在XX地址派25吨吊车，司机张超，车鲁AY8679，货物5吨"
      />
      <view class="voice-actions">
        <button class="adm-btn ghost" :class="{ recording }" @tap="toggleVoice">
          {{ recording ? '停止语音' : '语音输入' }}
        </button>
        <button class="adm-btn ghost" @tap="clearAll">清空</button>
      </view>
      <text class="voice-hint">
        {{ speechSupported ? '点击「语音输入」说话，或直接点击输入框使用键盘语音。' : '请点击输入框，使用手机键盘的语音听写。' }}
      </text>
      <view class="examples">
        <text class="examples-title">照着这样说</text>
        <view v-for="(e, i) in EXAMPLES" :key="i" class="example" @tap="useExample(e)">
          {{ e }}
        </view>
      </view>
      <button class="adm-btn primary" :disabled="!canParse || parsing" @tap="parse">
        {{ parsing ? '正在拆解...' : '智能拆解并填表' }}
      </button>
    </view>

    <view v-if="result" class="adm-section">
      <view class="result-head">
        <text class="adm-section-title">解析结果</text>
        <view class="result-tags">
          <text class="tag intent">{{ result.intent === 'DEMAND_CONVERT' ? '需求转工单' : '新建工单' }}</text>
          <text class="tag mode">{{ result.parseMode === 'LLM' ? '大模型' : '本地规则' }}</text>
        </view>
      </view>

      <view class="adm-kv"><text class="k">客户</text><text class="v strong">{{ val(result.customerName) }}</text></view>
      <view v-if="result.demandNo" class="adm-kv"><text class="k">需求单</text><text class="v">{{ result.demandNo }}</text></view>
      <view class="adm-kv"><text class="k">司机</text><text class="v">{{ val(result.staffName) }}</text></view>
      <view class="adm-kv"><text class="k">车辆</text><text class="v">{{ val(result.plateNo) }}</text></view>
      <view class="adm-kv"><text class="k">地址</text><text class="v">{{ val(result.address) }}</text></view>
      <view class="adm-kv"><text class="k">计划时间</text><text class="v">{{ fmtPlan(result.planStartAt) }}</text></view>
      <view class="adm-kv"><text class="k">吊车吨位</text><text class="v">{{ result.craneTonnage != null ? result.craneTonnage + ' 吨' : '-' }}</text></view>
      <view class="adm-kv"><text class="k">货物重量</text><text class="v">{{ result.cargoWeight != null ? result.cargoWeight + ' 吨' : '-' }}</text></view>
      <view class="adm-kv"><text class="k">备注</text><text class="v">{{ val(result.remark) }}</text></view>

      <view v-if="result.missingFields.length" class="adm-error">
        还需补填：{{ result.missingFields.join('、') }}
      </view>
      <view v-for="(w, i) in result.warnings" :key="i" class="adm-info">{{ w }}</view>

      <view class="adm-btn-row result-actions">
        <button class="adm-btn ghost" @tap="clearAll">重新输入</button>
        <button class="adm-btn primary" @tap="goFill">去填写并下发</button>
      </view>
      <button
        v-if="result.intent === 'DEMAND_CONVERT'"
        class="adm-btn ghost change-intent"
        @tap="forceCreate"
      >改为新建工单</button>
    </view>
  </view>
</template>

<style scoped>
.voice-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.char-count {
  font-size: 11px;
  color: #94a3b8;
}
.voice-textarea {
  width: 100%;
  box-sizing: border-box;
  min-height: 130px;
  margin-top: 8px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid #dbe3ef;
  background: #f8fafc;
  font-size: 15px;
  line-height: 1.7;
  color: #0f172a;
}
.voice-actions {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}
.voice-actions .adm-btn {
  flex: 1;
}
.voice-actions .recording {
  color: #fff;
  background: #dc2626;
  border-color: #dc2626;
}
.voice-hint {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}
.examples {
  margin: 12px 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px dashed #dbe3ef;
}
.examples-title {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #0e7490;
  margin-bottom: 6px;
}
.example {
  font-size: 12px;
  color: #475569;
  line-height: 1.6;
  padding: 6px 0;
  border-bottom: 1px dashed #e2e8f0;
}
.example:last-child {
  border-bottom: none;
}
.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.result-tags {
  display: flex;
  gap: 6px;
}
.tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
}
.tag.intent {
  color: #0e7490;
  background: #ecfeff;
  border: 1px solid #a5f3fc;
}
.tag.mode {
  color: #475569;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}
.result-actions {
  margin-top: 12px;
}
.change-intent {
  margin-top: 10px;
}
</style>
