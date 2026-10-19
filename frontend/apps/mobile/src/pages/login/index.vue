<script setup lang="ts">
// 登录页：仅保留「账号密码登录」与「微信授权登录」两种方式，登录后按角色分流
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
const password = ref('')
const showPwd = ref(false)

const phoneValid = computed(() => /^1\d{10}$/.test(phone.value))
const canSubmit = computed(() => phoneValid.value && password.value.length > 0)

// 一人多角色：登录成功后先选择本次进入的身份
const rolePickVisible = ref(false)
const switchingRole = ref(false)
const availableRoles = computed(() => auth.me?.availableRoles ?? [])
const ROLE_ICON: Record<string, string> = {
  ADMIN: 'settings-filled',
  DRIVER: 'staff-filled',
  CUSTOMER: 'person-filled'
}
const ROLE_DESC: Record<string, string> = {
  ADMIN: '需求审批、异常处理与移动办公',
  DRIVER: '接单、签到、签退与工单操作',
  CUSTOMER: '需求提报与工单进度查询'
}

onShow(async () => {
  await loadBranding()
  logoText.value = getBranding()['system.logo_text'] ?? '吊装'
  systemName.value = getBranding()['system.name'] ?? '吊装业务管理系统'
})

/** 管理员入口提示：账号密码登录后按角色进入移动办公台 */
function adminEntry() {
  uni.showToast({ title: '请使用管理员账号密码登录', icon: 'none' })
}

/** 登录成功后：多角色先选身份，单角色直接分流 */
async function afterLogin() {
  if (availableRoles.value.length > 1) {
    rolePickVisible.value = true
    return
  }
  routeByRole(auth.role)
}

/** 按角色进入对应端 */
function routeByRole(role: string) {
  if (role === 'DRIVER') {
    uni.reLaunch({ url: '/pages/driver/index' })
  } else if (role === 'CUSTOMER') {
    uni.reLaunch({ url: '/pages/customer/index' })
  } else if (role === 'ADMIN') {
    uni.reLaunch({ url: '/pages/admin/index' })
  } else {
    toastOk('当前账号角色暂不支持移动端')
  }
}

/** 选择本次进入身份：后端按所选角色重新签发令牌 */
async function chooseRole(code: string) {
  if (switchingRole.value) return
  switchingRole.value = true
  try {
    uni.showLoading({ title: '切换身份' })
    await auth.switchRole(code)
    uni.hideLoading()
    rolePickVisible.value = false
    routeByRole(code)
  } catch (e) {
    uni.hideLoading()
    toastError(e)
  } finally {
    switchingRole.value = false
  }
}

async function doLogin() {
  if (!canSubmit.value) {
    uni.showToast({ title: '请填写手机号与密码', icon: 'none' })
    return
  }
  try {
    uni.showLoading({ title: '登录中' })
    // 账号密码登录：用户名即手机号；30015（已关闭密码登录）等业务错误直接 toast 后端 message
    await auth.loginByPassword(phone.value, password.value)
    uni.hideLoading()
    await afterLogin()
  } catch (e) {
    uni.hideLoading()
    toastError(e)
  }
}

function goAgreement() {
  uni.navigateTo({ url: '/pages/agreement' })
}

/** 微信一键登录（仅小程序通道渲染）：uni.login 取 code → 已绑定直登，未绑定引导绑定预留手机号 */
const wxLogging = ref(false)
const bindVisible = ref(false)
const bindTicket = ref('')
const bindPhone = ref('')
const bindCode = ref('')
const bindSending = ref(false)
const bindCountdown = ref(0)
const bindSubmitting = ref(false)
let bindTimer: ReturnType<typeof setInterval> | null = null

const bindPhoneValid = computed(() => /^1\d{10}$/.test(bindPhone.value))
const bindCanSubmit = computed(() => bindPhoneValid.value && bindCode.value.length === 6)

/** uni.login 取微信登录 code（单次有效） */
function getWechatCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (res) => {
        if (res.code) resolve(res.code)
        else reject(new Error('微信授权失败，请重试'))
      },
      fail: (err) => reject(new Error(err.errMsg || '微信授权失败，请重试'))
    })
  })
}

async function wechatLogin() {
  if (wxLogging.value) return
  wxLogging.value = true
  try {
    uni.showLoading({ title: '微信登录中' })
    const code = await getWechatCode()
    const res = await auth.wechatLogin(code)
    uni.hideLoading()
    if (res.needBind) {
      // 首次登录：凭 bindTicket 绑定管理员预留手机号后激活
      bindTicket.value = res.bindTicket || ''
      bindPhone.value = ''
      bindCode.value = ''
      bindVisible.value = true
      return
    }
    await afterLogin()
  } catch (e) {
    uni.hideLoading()
    toastError(e)
  } finally {
    wxLogging.value = false
  }
}

/** 绑定场景验证码（BIND_PHONE） */
async function getBindCode() {
  if (!bindPhoneValid.value) {
    uni.showToast({ title: '请输入 11 位手机号', icon: 'none' })
    return
  }
  if (bindCountdown.value > 0 || bindSending.value) return
  bindSending.value = true
  try {
    const res = await sendSmsCode(bindPhone.value, 'BIND_PHONE')
    bindCountdown.value = res.interval || 60
    bindTimer = setInterval(() => {
      bindCountdown.value -= 1
      if (bindCountdown.value <= 0 && bindTimer) {
        clearInterval(bindTimer)
        bindTimer = null
      }
    }, 1000)
    if (res.debugCode) {
      bindCode.value = res.debugCode
      uni.showToast({ title: '验证码已发送（开发模式自动填入）', icon: 'none' })
    } else {
      uni.showToast({ title: '验证码已发送', icon: 'none' })
    }
  } catch (e) {
    toastError(e)
  } finally {
    bindSending.value = false
  }
}

async function doBind() {
  if (!bindCanSubmit.value) {
    uni.showToast({ title: '请填写手机号与验证码', icon: 'none' })
    return
  }
  if (!bindTicket.value) {
    uni.showToast({ title: '绑定凭据已失效，请重新微信登录', icon: 'none' })
    cancelBind()
    return
  }
  if (bindSubmitting.value) return
  bindSubmitting.value = true
  try {
    uni.showLoading({ title: '绑定中' })
    await auth.bindPhone(bindPhone.value, bindCode.value, bindTicket.value)
    uni.hideLoading()
    bindVisible.value = false
    await afterLogin()
  } catch (e) {
    uni.hideLoading()
    toastError(e)
  } finally {
    bindSubmitting.value = false
  }
}

function cancelBind() {
  bindVisible.value = false
  bindTicket.value = ''
  bindPhone.value = ''
  bindCode.value = ''
  bindCountdown.value = 0
  if (bindTimer) {
    clearInterval(bindTimer)
    bindTimer = null
  }
}

onUnmounted(() => {
  if (bindTimer) clearInterval(bindTimer)
})
</script>

<template>
  <view class="login-page">
    <view class="brand">
      <view class="logo">{{ logoText }}</view>
      <text class="name">{{ systemName }}</text>
      <text class="sub">账号密码 / 微信授权统一入口</text>
    </view>

    <view class="form">
      <view class="role-entry">
        <text class="role-note">仅支持账号密码与微信授权登录</text>
        <text class="role-link" @tap="adminEntry">管理员入口</text>
      </view>
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
        <text class="label">密码</text>
        <input
          v-model="password"
          class="input"
          :password="!showPwd"
          maxlength="64"
          placeholder="请输入密码"
          placeholder-class="ph"
        />
        <view class="code-btn" @tap="showPwd = !showPwd">{{ showPwd ? '隐藏' : '显示' }}</view>
      </view>
      <button class="submit" :disabled="!canSubmit" @click="doLogin">账号密码登录</button>
      <!-- #ifdef MP-WEIXIN -->
      <view class="login-divider"><text>或</text></view>
      <button class="wechat" :disabled="wxLogging" @click="wechatLogin">
        <uni-icons type="weixin" size="20" color="#ffffff" />
        <text>微信授权登录</text>
      </button>
      <!-- #endif -->
    </view>

    <view class="footer">
      <text class="agreement" @click="goAgreement">用户协议与隐私政策</text>
    </view>

    <!-- 一人多角色：登录后选择本次进入身份 -->
    <view v-if="rolePickVisible" class="role-mask">
      <view class="role-panel">
        <text class="role-kicker">IDENTITY · SELECT</text>
        <text class="role-heading">选择进入身份</text>
        <text class="role-hint">该账号开通了多个角色，请选择本次要使用的身份</text>
        <view class="role-list">
          <view
            v-for="r in availableRoles"
            :key="r.code"
            class="role-card"
            :class="{ busy: switchingRole }"
            @tap="chooseRole(r.code)"
          >
            <view class="role-icon">
              <uni-icons :type="ROLE_ICON[r.code] || 'person-filled'" size="24" color="#0b1f3a" />
            </view>
            <view class="role-info">
              <text class="role-name">{{ r.name }}</text>
              <text class="role-desc">{{ ROLE_DESC[r.code] || '进入对应工作台' }}</text>
            </view>
            <uni-icons type="right" size="18" color="#7dd3fc" />
          </view>
        </view>
      </view>
    </view>

    <!-- 微信授权后绑定管理员预留手机号（首次登录激活） -->
    <view v-if="bindVisible" class="role-mask">
      <view class="bind-panel">
        <text class="role-kicker">WECHAT · BIND</text>
        <text class="role-heading">绑定预留手机号</text>
        <text class="role-hint">首次微信登录需绑定管理员预留的手机号完成激活</text>
        <view class="bind-field">
          <text class="bind-label">手机号</text>
          <input
            v-model="bindPhone"
            class="bind-input"
            type="number"
            maxlength="11"
            placeholder="请输入预留手机号"
            placeholder-class="ph"
          />
        </view>
        <view class="bind-field">
          <text class="bind-label">验证码</text>
          <input
            v-model="bindCode"
            class="bind-input"
            type="number"
            maxlength="6"
            placeholder="请输入验证码"
            placeholder-class="ph"
          />
          <view
            class="code-btn"
            :class="{ disabled: bindCountdown > 0 || bindSending || !bindPhoneValid }"
            @click="getBindCode"
          >
            {{ bindCountdown > 0 ? bindCountdown + 's 后重发' : '获取验证码' }}
          </view>
        </view>
        <button class="bind-submit" :disabled="!bindCanSubmit || bindSubmitting" @click="doBind">绑定并登录</button>
        <text class="bind-cancel" @click="cancelBind">暂不绑定，返回登录</text>
      </view>
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
  width: 68px;
  height: 68px;
  border-radius: 16px;
  background: linear-gradient(135deg, #0b1f3a, #0e7490);
  border: 1px solid rgba(103, 232, 249, 0.45);
  color: #a5f3fc;
  font-size: 25px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 22px rgba(11, 31, 58, 0.2);
}
.name {
  font-size: 22px;
  font-weight: 700;
  color: var(--txt);
}
.sub {
  font-size: 14px;
  color: var(--sub);
}
.form {
  margin-top: 32px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.role-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: -6px;
  padding: 8px 12px;
  border-radius: 10px;
  background: linear-gradient(135deg, #eff6ff, #ecfeff);
  border: 1px solid #dbeafe;
}
.role-note {
  font-size: 12px;
  color: var(--sub);
}
.role-link {
  font-size: 13px;
  font-weight: 600;
  color: #0e7490;
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
.submit {
  margin-top: 8px;
  height: 50px;
  line-height: 50px;
  border-radius: var(--radius);
  background: linear-gradient(135deg, #1a6bff, #0b4fd6);
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  box-shadow: 0 6px 16px rgba(26, 107, 255, 0.22);
}
.submit[disabled] {
  opacity: 0.5;
  color: #fff;
  background: var(--brand);
}
.login-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--sub);
  font-size: 12px;
}
.login-divider::before,
.login-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--line);
}
.wechat {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 50px;
  line-height: 1;
  border-radius: var(--radius);
  background: #07c160;
  color: #fff;
  font-size: 17px;
  font-weight: 600;
}
.wechat::after {
  border: none;
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

/* 多角色身份选择：深色指挥台风格，与移动办公台视觉统一 */
.role-mask {
  position: fixed;
  inset: 0;
  z-index: 999;
  display: flex;
  align-items: flex-end;
  background: rgba(4, 12, 26, 0.72);
  backdrop-filter: blur(6px);
}
.role-panel {
  width: 100%;
  box-sizing: border-box;
  padding: 24px 20px calc(28px + env(safe-area-inset-bottom));
  border-radius: 24px 24px 0 0;
  background: linear-gradient(160deg, #0b1f3a 0%, #123a6b 58%, #0e7490 100%);
  box-shadow: 0 -12px 40px rgba(4, 12, 26, 0.45);
}
.role-kicker {
  display: block;
  font-size: 11px;
  letter-spacing: 2px;
  color: rgba(125, 211, 252, 0.8);
}
.role-heading {
  display: block;
  margin-top: 6px;
  font-size: 22px;
  font-weight: 700;
  color: #fff;
}
.role-hint {
  display: block;
  margin-top: 6px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.62);
}
.role-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 18px;
}
.role-card {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 68px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(125, 211, 252, 0.22);
}
.role-card:active {
  background: rgba(125, 211, 252, 0.16);
  border-color: rgba(125, 211, 252, 0.55);
}
.role-card.busy {
  opacity: 0.6;
}
.role-icon {
  flex: none;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #a5f3fc, #67e8f9);
  box-shadow: 0 4px 14px rgba(34, 211, 238, 0.28);
}
.role-info {
  flex: 1;
  min-width: 0;
}
.role-name {
  display: block;
  font-size: 17px;
  font-weight: 700;
  color: #fff;
}
.role-desc {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.58);
}

/* 微信授权后绑定手机号：与身份选择面板同一深色视觉 */
.bind-panel {
  width: 100%;
  box-sizing: border-box;
  padding: 24px 20px calc(28px + env(safe-area-inset-bottom));
  border-radius: 24px 24px 0 0;
  background: linear-gradient(160deg, #0b1f3a 0%, #123a6b 58%, #0e7490 100%);
  box-shadow: 0 -12px 40px rgba(4, 12, 26, 0.45);
  display: flex;
  flex-direction: column;
}
.bind-field {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(125, 211, 252, 0.22);
}
.bind-label {
  flex: none;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.82);
}
.bind-input {
  flex: 1;
  min-height: 24px;
  font-size: 15px;
  color: #fff;
}
.bind-panel .ph {
  color: rgba(255, 255, 255, 0.4);
}
.bind-panel .code-btn {
  border-left-color: rgba(125, 211, 252, 0.3);
  color: #7dd3fc;
}
.bind-panel .code-btn.disabled {
  color: rgba(255, 255, 255, 0.35);
}
.bind-submit {
  margin-top: 20px;
  height: 48px;
  line-height: 48px;
  border-radius: 12px;
  background: #07c160;
  color: #fff;
  font-size: 17px;
  font-weight: 600;
}
.bind-submit[disabled] {
  opacity: 0.5;
  background: #07c160;
  color: #fff;
}
.bind-cancel {
  margin-top: 14px;
  text-align: center;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
  text-decoration: underline;
}
</style>
