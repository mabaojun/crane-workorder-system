// 移动端请求客户端接入层：向共享层注入端别、令牌、baseURL 与传输适配
import { ApiError } from '@crane/shared/api/client'
import type { ClientType, RequestAdapter, TokenAdapter } from '@crane/shared/api/client'

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

// API 根地址：H5 用同源相对路径（vite 代理 / nginx 同域）；小程序必须是完整域名
// 本地联调：VITE_API_ORIGIN=http://<本机IP>:7777 重新构建，并在开发者工具勾选「不校验合法域名」
// #ifdef MP-WEIXIN
const API_ORIGIN = import.meta.env.VITE_API_ORIGIN || 'https://jndys.top'
// #endif
// #ifndef MP-WEIXIN
const API_ORIGIN = ''
// #endif

export const API_BASE_URL = API_ORIGIN + '/api/v1'

/** 相对地址补全为完整地址：小程序附件/图片链接必须绝对 HTTPS 地址，已是完整地址则原样返回 */
export function absUrl(url: string): string {
  if (/^https?:\/\//i.test(url)) return url
  return API_ORIGIN + url
}

/** 小程序端传输适配：uni.request（即 wx.request）+ 超时，错误语义与 fetch 版一致 */
export const uniRequestAdapter: RequestAdapter = ({ url, method, headers, body, timeoutMs }) =>
  new Promise((resolve, reject) => {
    uni.request({
      url,
      method: method as UniApp.RequestOptions['method'],
      data: body ? JSON.parse(body) : undefined,
      header: headers,
      timeout: timeoutMs,
      success: (res) => resolve({ status: res.statusCode, data: res.data }),
      fail: (err) => {
        const msg = String(err?.errMsg || '')
        reject(
          msg.includes('timeout')
            ? new ApiError(50008, '请求超时，请检查网络后重试', '')
            : new ApiError(-1, '网络连接失败，请检查网络后重试', '')
        )
      }
    })
  })
