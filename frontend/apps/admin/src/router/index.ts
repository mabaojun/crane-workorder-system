import { createRouter, createWebHistory } from 'vue-router'
import { configureClient } from '@crane/shared/api/client'
import { systemName } from '@crane/shared/api/branding'
import { useAuthStore } from '../stores/auth'

configureClient({
  clientType: 'admin',
  onUnauthorized: () => {
    localStorage.removeItem('crane_token')
    const base = import.meta.env.BASE_URL.replace(/\/$/, '')
    if (!location.pathname.startsWith(`${base}/login`)) location.href = `${base}/login`
  }
})

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/login/index.vue') },
    {
      path: '/',
      component: () => import('../layouts/AdminLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard/cockpit' },
        {
          path: 'dashboard/cockpit',
          name: 'cockpit',
          meta: { title: '工作台', menu: 'dashboard' },
          component: () => import('../views/dashboard/cockpit.vue')
        },
        {
          path: 'dashboard/staff-status',
          name: 'staff-status',
          meta: { title: '人员状态', menu: 'dashboard', adminOnly: true },
          component: () => import('../views/dashboard/staff-status.vue')
        },
        {
          path: 'dashboard/vehicle-status',
          name: 'vehicle-status',
          meta: { title: '车辆状态', menu: 'dashboard', adminOnly: true },
          component: () => import('../views/dashboard/vehicle-status.vue')
        },
        {
          path: 'dashboard/order-status',
          name: 'order-status',
          meta: { title: '订单状态', menu: 'dashboard', adminOnly: true },
          component: () => import('../views/dashboard/order-status.vue')
        },
        {
          path: 'demand/list',
          name: 'demand-list',
          meta: { title: '需求单', menu: 'demand', adminOnly: true },
          component: () => import('../views/demand/list.vue')
        },
        {
          path: 'workorder/list',
          name: 'workorder-list',
          meta: { title: '工单管理', menu: 'workorder', adminOnly: true },
          component: () => import('../views/workorder/list.vue')
        },
        {
          path: 'workorder/detail/:id',
          name: 'workorder-detail',
          meta: { title: '工单详情', menu: 'workorder', adminOnly: true },
          component: () => import('../views/workorder/detail.vue')
        },
        {
          path: 'dispatch/pending',
          name: 'dispatch-pending',
          meta: { title: '异常工单', menu: 'dispatch', adminOnly: true },
          component: () => import('../views/dispatch/pending.vue')
        },
        {
          path: 'staff/list',
          name: 'staff-list',
          meta: { title: '员工档案', menu: 'staff', adminOnly: true },
          component: () => import('../views/staff/list.vue')
        },
        {
          path: 'staff/vehicle-list',
          name: 'vehicle-list',
          meta: { title: '车辆档案', menu: 'vehicle', adminOnly: true },
          component: () => import('../views/staff/vehicle-list.vue')
        },
        {
          path: 'customer/list',
          name: 'customer-list',
          meta: { title: '客户档案', menu: 'customer', adminOnly: true },
          component: () => import('../views/customer/list.vue')
        },
        {
          path: 'system/place-list',
          name: 'place-list',
          meta: { title: '常用施工地点', menu: 'place', adminOnly: true },
          component: () => import('../views/system/place-list.vue')
        },
        {
          path: 'system/user-list',
          name: 'user-list',
          meta: { title: '账号与权限', menu: 'user', adminOnly: true },
          component: () => import('../views/system/user-list.vue')
        },
        {
          path: 'system/config',
          name: 'system-config',
          meta: { title: '系统配置', menu: 'config', adminOnly: true },
          component: () => import('../views/system/config.vue')
        },
        {
          path: 'exception/list',
          name: 'exception-list',
          meta: { title: '异常处理', menu: 'exception', adminOnly: true },
          component: () => import('../views/exception/list.vue')
        }
      ]
    }
  ]
})

router.beforeEach(async (to) => {
  const token = localStorage.getItem('crane_token')
  if (to.path !== '/login' && !token) return '/login'
  if (to.path === '/login' && token) return '/'
  // 角色过滤：adminOnly 路由仅 ADMIN 可入（本期后台仅管理员使用）
  if (token) {
    const auth = useAuthStore()
    if (!auth.me) {
      try {
        await auth.fetchMe()
      } catch {
        return '/login'
      }
    }
    if (to.meta?.adminOnly && auth.me?.role !== 'ADMIN') return '/dashboard/cockpit'
  }
  document.title = to.meta?.title ? `${to.meta.title} - ${systemName()}` : systemName()
  return true
})

export default router
