import { createRouter, createWebHistory } from 'vue-router'
import { configureClient } from '@crane/shared/api/client'
import { systemName } from '@crane/shared/api/branding'

configureClient({
  clientType: 'admin',
  onUnauthorized: () => {
    localStorage.removeItem('crane_token')
    if (location.pathname !== '/login') location.href = '/login'
  }
})

const router = createRouter({
  history: createWebHistory(),
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
        }
      ]
    }
  ]
})

router.beforeEach((to) => {
  const token = localStorage.getItem('crane_token')
  if (to.path !== '/login' && !token) return '/login'
  if (to.path === '/login' && token) return '/'
  document.title = to.meta?.title ? `${to.meta.title} - ${systemName()}` : systemName()
  return true
})

export default router
