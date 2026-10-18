// 工具：时间格式化与定位封装
import dayjs from 'dayjs'

export function fmtTime(v?: string | null, tpl = 'MM-DD HH:mm') {
  if (!v) return '-'
  const d = dayjs(v)
  return d.isValid() ? d.format(tpl) : '-'
}

export function fmtDateTime(v?: string | null) {
  return fmtTime(v, 'YYYY-MM-DD HH:mm')
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

/** 统一错误提示：ApiError 直接展示后端 message（含 30005 超范围等业务码文案） */
export function toastError(e: unknown) {
  const msg = e instanceof Error ? e.message : '操作失败，请重试'
  uni.showToast({ title: msg, icon: 'none' })
}

export function toastOk(msg = '操作成功') {
  uni.showToast({ title: msg, icon: 'success' })
}
