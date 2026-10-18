// 一键导航：小程序走微信内置地图（自带"到这去"导航），H5 跳高德 URI API

export interface NavigationTarget {
  lng: number
  lat: number
  name?: string
  address?: string
}

/** 无有效坐标时统一提示 */
function invalidCoords(t: NavigationTarget): boolean {
  const lng = Number(t.lng)
  const lat = Number(t.lat)
  if (!Number.isFinite(lng) || !Number.isFinite(lat) || (lng === 0 && lat === 0)) {
    uni.showToast({ title: '该工单无位置坐标', icon: 'none' })
    return true
  }
  return false
}

export function openNavigation(t: NavigationTarget): void {
  if (invalidCoords(t)) return
  // #ifdef MP-WEIXIN
  uni.openLocation({
    latitude: Number(t.lat),
    longitude: Number(t.lng),
    name: t.name || t.address || '目的地',
    address: t.address || '',
    fail: () => uni.showToast({ title: '打开地图失败', icon: 'none' })
  })
  // #endif
  // #ifdef H5
  const name = encodeURIComponent(t.name || t.address || '目的地')
  window.location.href = `https://uri.amap.com/navigation?to=${Number(t.lng)},${Number(t.lat)},${name}&mode=car&src=crane&callnative=1`
  // #endif
}

/** 工单快捷构造：有名称用任务名，否则用地址 */
export function navFromOrder(o: { taskName?: string; orderNo: string; address?: string; longitude?: number; latitude?: number }): NavigationTarget | null {
  if (o.longitude == null || o.latitude == null) return null
  return {
    lng: o.longitude,
    lat: o.latitude,
    name: o.taskName || o.orderNo,
    address: o.address
  }
}

