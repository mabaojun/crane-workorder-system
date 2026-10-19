// 认证状态：token / me / role / 登录 / 登出 / 未授权处理
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { uniTokenAdapter } from '../api/client'
import {
  bindPhone as bindPhoneApi,
  fetchMe,
  logout,
  passwordLogin,
  selectRole,
  smsLogin,
  wechatBindPhone as wechatBindPhoneApi,
  wechatLogin as wechatLoginApi
} from '../api/modules/auth'
import type { MeInfo } from '../api/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(uniTokenAdapter.get() ?? '')
  const me = ref<MeInfo | null>(null)
  const role = computed(() => me.value?.role ?? '')

  async function login(phone: string, smsCode: string) {
    const res = await smsLogin(phone, smsCode)
    token.value = res.accessToken
    uniTokenAdapter.set(res.accessToken)
    await loadMe()
  }

  /** 静态密码登录：用户名即手机号，成功后与验证码登录同流程分流 */
  async function loginByPassword(username: string, password: string) {
    const res = await passwordLogin(username, password)
    token.value = res.accessToken
    uniTokenAdapter.set(res.accessToken)
    await loadMe()
  }

  async function loadMe() {
    me.value = await fetchMe()
  }

  /** 微信授权登录：未绑定手机号时返回 bindTicket，由登录页继续引导绑定 */
  async function wechatLogin(code: string): Promise<{ needBind: boolean; bindTicket?: string }> {
    const res = await wechatLoginApi(code, 'mp')
    if (!res.bound) {
      return { needBind: true, bindTicket: res.bindTicket }
    }
    token.value = res.accessToken
    uniTokenAdapter.set(res.accessToken)
    await loadMe()
    return { needBind: false }
  }

  /** 微信授权后绑定预留手机号，成功后与验证码登录同流程 */
  async function bindPhone(phone: string, smsCode: string, bindTicket: string) {
    const res = await bindPhoneApi(phone, smsCode, bindTicket)
    token.value = res.accessToken
    uniTokenAdapter.set(res.accessToken)
    await loadMe()
  }

  /** 微信手机号快速验证绑定：phoneCode 由 getPhoneNumber 返回，免短信验证码 */
  async function bindPhoneByWechat(phoneCode: string, bindTicket: string) {
    const res = await wechatBindPhoneApi(phoneCode, bindTicket)
    token.value = res.accessToken
    uniTokenAdapter.set(res.accessToken)
    await loadMe()
  }

  /** 多角色账号：按所选身份重新签发令牌并刷新个人信息 */
  async function switchRole(roleCode: string) {
    const res = await selectRole(roleCode)
    token.value = res.accessToken
    uniTokenAdapter.set(res.accessToken)
    await loadMe()
  }

  async function doLogout() {
    try {
      if (token.value) await logout()
    } catch {
      // 登出接口失败不阻断本地清理
    }
    clear()
  }

  function clear() {
    token.value = ''
    me.value = null
    uniTokenAdapter.clear()
  }

  /** 令牌失效（10001/10002/10004）：清理并回登录页 */
  function handleUnauthorized() {
    if (!token.value) return
    clear()
    uni.reLaunch({ url: '/pages/login/index' })
  }

  return {
    token,
    me,
    role,
    login,
    loginByPassword,
    wechatLogin,
    bindPhone,
    bindPhoneByWechat,
    loadMe,
    switchRole,
    doLogout,
    clear,
    handleUnauthorized
  }
})
