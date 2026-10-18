<script setup lang="ts">
// 登录页（T-30）：验证码直登 + 角色分流 + 微信一键登录占位（小程序通道）
import { computed, onUnmounted, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getBranding, loadBranding } from '@crane/shared/api/branding'
import { sendSmsCode } from '../../api/modules/auth'
import { useAuthStore } from '../../stores/auth'
import { toastError, toastOk } from '../../utils'

const auth = useAuthStore()

const logoText = ref('吊装')
const systemName = ref('吊装业务管理系统')
const phone = ref('')
const smsCode = ref('')
const sending = ref(false)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const phoneValid = computed(() => /^1\d{10}$/.test(phone.value))
const canSubmit = computed(() => phoneValid.value && smsCode.value.length > 0)

onShow(async () => {
  await loadBranding()
  logoText.value = getBranding()['system.logo_text'] ?? '吊装'
  systemName.value = getBranding()['system.name'] ?? '吊装业务管理系统'
})

async function getCode() {
  if (!phoneValid.value) {
    uni.showToast({ title: '请输入 11 位手机号', icon: 'none' })
    return
  }
  if (countdown.value > 0 || sending.value) return
  sending.value = true
  try {
    const res = await sendSmsCode(phone.value)
    countdown.value = res.interval || 60
    timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0 && timer) {
        clearInterval(timer)
        timer = null
      }
    }, 1000)
    if (res.debugCode) {
      // MOCK 短信通道：开发阶段自动回填验证码；生产环境不带 debugCode，此处静默容错
      smsCode.value = res.debugCode
      console.info('[sms] MOCK 验证码已自动填入：', res.debugCode)
      uni.showToast({ title: '验证码已发送（开发模式自动填入）', icon: 'none' })
    } else {
      uni.showToast({ title: '验证码已发送', icon: 'none' })
    }
  } catch (e) {
    toastError(e)
  } finally {
    sending.value = false
  }
}

async function doLogin() {
  if (!canSubmit.value) {
    uni.showToast({ title: '请填写手机号与验证码', icon: 'none' })
    return
  }
  try {
    uni.showLoading({ title: '登录中' })
    await auth.login(phone.value, smsCode.value)
    uni.hideLoading()
    const r = auth.role
    if (r === 'DRIVER') {
      uni.reLaunch({ url: '/pages/driver/index' })
    } else if (r === 'CUSTOMER') {
      uni.reLaunch({ url: '/pages/customer/index' })
    } else {
      toastOk('管理员请使用 PC 后台')
    }
  } catch (e) {
    uni.hideLoading()
    toastError(e)
  }
}

function goAgreement() {
  uni.navigateTo({ url: '/pages/agreement' })
}

/** 微信一键登录（仅小程序通道渲染）：MVP 骨架，待后端配置微信通道后对接 */
function wechatLogin() {
  // TODO(二期): uni.login({ provider: 'weixin' }) 取 code → POST /auth/wechat-login
  uni.showToast({ title: '微信登录通道二期开放', icon: 'none' })
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <view class="login-page">
    <view class="brand">
      <view class="logo">{{ logoText }}</view>
      <text class="name">{{ systemName }}</text>
      <text class="sub">司机 / 客户移动端</text>
    </view>

    <view class="form">
      <view class="field">
        <text class="label">手机号</text>
        <input
          v-model="phone"
          class="input"
          type="number"
          maxlength="11"
          placeholder="请输入手机号"
          placeholder-class="ph"
        />
      </view>
      <view class="field">
        <text class="label">验证码</text>
        <input
          v-model="smsCode"
          class="input"
          type="number"
          maxlength="6"
          placeholder="请输入验证码"
          placeholder-class="ph"
        />
        <view class="code-btn" :class="{ disabled: countdown > 0 || sending || !phoneValid }" @click="getCode">
          {{ countdown > 0 ? countdown + 's 后重发' : '获取验证码' }}
        </view>
      </view>
      <button class="submit" :disabled="!canSubmit" @click="doLogin">登录</button>
      <!-- #ifdef MP-WEIXIN -->
      <button class="wechat" @click="wechatLogin">微信一键登录</button>
      <!-- #endif -->
    </view>

    <view class="footer">
      <text class="agreement" @click="goAgreement">用户协议与隐私政策</text>
    </view>
  </view>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
  padding: 0 24px;
  box-sizing: border-box;
}
.brand {
  margin-top: 88px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.logo {
  width: 64px;
  height: 64px;
  border-radius: 12px;
  background: var(--brand);
  color: #fff;
  font-size: 24px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.name {
  font-size: 20px;
  font-weight: 600;
  color: var(--txt);
}
.sub {
  font-size: 13px;
  color: var(--sub);
}
.form {
  margin-top: 40px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.field {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 12px 16px;
}
.label {
  flex: none;
  font-size: 15px;
  color: var(--txt);
}
.input {
  flex: 1;
  font-size: 15px;
  min-height: 24px;
}
.ph {
  color: var(--sub);
}
.code-btn {
  flex: none;
  font-size: 13px;
  color: var(--brand);
  padding: 4px 0 4px 12px;
  border-left: 1px solid var(--line);
}
.code-btn.disabled {
  color: var(--sub);
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
.submit[disabled] {
  opacity: 0.5;
  color: #fff;
  background: var(--brand);
}
.wechat {
  height: 48px;
  line-height: 48px;
  border-radius: var(--radius);
  background: #07c160;
  color: #fff;
  font-size: 17px;
}
.footer {
  margin-top: auto;
  padding: 24px 0 calc(24px + env(safe-area-inset-bottom));
  display: flex;
  justify-content: center;
}
.agreement {
  font-size: 13px;
  color: var(--sub);
  text-decoration: underline;
}
</style>
