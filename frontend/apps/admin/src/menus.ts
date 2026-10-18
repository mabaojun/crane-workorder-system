import { computed } from 'vue'
import { useAuthStore } from './stores/auth'

export interface MenuItem {
  key: string
  title: string
  path?: string
  children?: MenuItem[]
}

/** 后台菜单配置：数据驱动，adminOnly 项仅 ADMIN 角色可见 */
export const MENU_CONFIG: (MenuItem & { adminOnly?: boolean })[] = [
  { key: 'dashboard', title: '工作台', path: '/dashboard/cockpit' },
  { key: 'demand', title: '需求单', path: '/demand/list', adminOnly: true },
  { key: 'workorder', title: '工单管理', path: '/workorder/list', adminOnly: true },
  { key: 'dispatch', title: '派单中心', path: '/dispatch/pending', adminOnly: true },
  {
    key: 'archive',
    title: '档案管理',
    adminOnly: true,
    children: [
      { key: 'staff', title: '员工', path: '/staff/list' },
      { key: 'vehicle', title: '车辆', path: '/staff/vehicle-list' },
      { key: 'customer', title: '客户', path: '/customer/list' },
      { key: 'place', title: '常用施工地点', path: '/system/place-list' }
    ]
  },
  { key: 'user', title: '账号与权限', path: '/system/user-list', adminOnly: true },
  { key: 'config', title: '系统配置', path: '/system/config', adminOnly: true },
  { key: 'exception', title: '异常处理', path: '/exception/list', adminOnly: true }
]

/** 按角色过滤后的可见菜单 */
export function useVisibleMenus() {
  const auth = useAuthStore()
  return computed<MenuItem[]>(() => {
    const isAdmin = auth.me?.role === 'ADMIN'
    return MENU_CONFIG
      .filter((item) => !item.adminOnly || isAdmin)
      .map(({ adminOnly: _adminOnly, ...item }) => item)
  })
}
