// 工具：时间格式化与定位封装
import dayjs from 'dayjs'
import { absUrl, CLIENT_TYPE, uniTokenAdapter } from '../api/client'

export function fmtTime(v?: string | null, tpl = 'MM-DD HH:mm') {
  if (!v) return '-'
  const d = dayjs(v)
  return d.isValid() ? d.format(tpl) : '-'
}

export function fmtDateTime(v?: string | null) {
  return fmtTime(v, 'YYYY-MM-DD HH:mm')
}

/** 年月日时分 + 今天/明天/后天提示（其他日期只显示时间） */
export function fmtTimeWithDayHint(v?: string | null, now: number = Date.now()) {
  if (!v) return '-'
  const d = dayjs(v)
  if (!d.isValid()) return '-'
  const diff = d.startOf('day').diff(dayjs(now).startOf('day'), 'day')
  const hint = diff === 0 ? '今天' : diff === 1 ? '明天' : diff === 2 ? '后天' : ''
  return d.format('YYYY-MM-DD HH:mm') + (hint ? `（${hint}）` : '')
}

const IMAGE_EXT = /\.(jpe?g|png|gif|webp|bmp|svg|avif)$/i

/** 是否为可预览图片附件 */
export function isImageAttachment(name?: string | null) {
  return IMAGE_EXT.test(name || '')
}

/** 鉴权拉取附件并转 data URL，用于点击附件后的弹窗预览 */
export function loadAttachmentPreviewUrl(url: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const token = uniTokenAdapter.get()
    const header: Record<string, string> = { 'X-Client': CLIENT_TYPE }
    if (token) header.Authorization = `Bearer ${token}`
    uni.request({
      url: absUrl(url),
      method: 'GET',
      responseType: 'arraybuffer',
      header,
      success: (res: any) => {
        if (res.statusCode !== 200 || !res.data) {
          reject(new Error('图片加载失败'))
          return
        }
        const rawType = res.header?.['Content-Type'] || res.header?.['content-type'] || 'image/jpeg'
        const contentType = String(rawType).split(';')[0]
        resolve(`data:${contentType};base64,${uni.arrayBufferToBase64(res.data)}`)
      },
      fail: () => reject(new Error('图片加载失败，请稍后重试'))
    })
  })
}

/** H5/小程序统一 Promise 化定位（gcj02） */
export function getLocation(): Promise<{ longitude: number; latitude: number }> {
  return new Promise((resolve, reject) => {
    uni.getLocation({
      type: 'gcj02',
      success: (res) => resolve({ longitude: res.longitude, latitude: res.latitude }),
      fail: (err) => reject(new Error(err.errMsg || '定位失败'))
    })
  })
}

/** Promise 化选图（返回本地临时/ base64 预览地址） */
export function chooseImages(count = 1): Promise<string[]> {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count,
      sizeType: ['compressed'],
      success: (res) => resolve(res.tempFilePaths as string[]),
      fail: (err) => reject(new Error(err.errMsg || '未选择照片'))
    })
  })
}

/** 上传本地图片到文件服务，返回鉴权下载地址（需求配图 / 打卡照片统一入口） */
export function uploadLocalFile(filePath: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const token = uniTokenAdapter.get()
    uni.uploadFile({
      url: absUrl('/api/v1/files/upload'),
      filePath,
      name: 'file',
      header: {
        'X-Client': CLIENT_TYPE,
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success: (res) => {
        try {
          const body = JSON.parse(res.data)
          if (body.code === 0 && body.data?.url) {
            resolve(body.data.url)
          } else {
            reject(new Error(body.message || '附件上传失败'))
          }
        } catch {
          reject(new Error('附件上传响应解析失败'))
        }
      },
      fail: () => reject(new Error('附件上传失败，请检查网络'))
    })
  })
}

/** 统一错误提示：ApiError 直接展示后端 message（含 30005 超范围等业务码文案） */
export function toastError(e: unknown) {
  const msg = e instanceof Error ? e.message : '操作失败，请重试'
  uni.showToast({ title: msg, icon: 'none' })
}

export function toastOk(msg = '操作成功') {
  uni.showToast({ title: msg, icon: 'success' })
}
