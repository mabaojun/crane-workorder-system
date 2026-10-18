// 移动端请求客户端接入层：向共享层注入端别与令牌适配
// H5 通道本期运行验证；mp-weixin 通道仅要求编译通过，fetch 运行时差异留待二期换 uni.request 适配
import type { ClientType, TokenAdapter } from '@crane/shared/api/client'

export let CLIENT_TYPE: ClientType = 'h5'
// #ifdef MP-WEIXIN
CLIENT_TYPE = 'mp'
// #endif

/** 统一用 uni storage 存取令牌：H5 下即 localStorage，小程序下为本地存储 */
export const uniTokenAdapter: TokenAdapter = {
  get: () => {
    const v = uni.getStorageSync('crane_token')
    return v ? String(v) : null
  },
  set: (t) => uni.setStorageSync('crane_token', t),
  clear: () => uni.removeStorageSync('crane_token')
}
