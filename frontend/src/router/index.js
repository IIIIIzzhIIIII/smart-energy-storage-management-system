import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/',
    component: () => import('../layout/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '实时大屏' }
      },
      {
        path: 'device',
        name: 'Device',
        component: () => import('../views/Device.vue'),
        meta: { title: '设备管理' }
      },
      {
        path: 'safety',
        name: 'Safety',
        component: () => import('../views/Safety.vue'),
        meta: { title: '储能安全检测' }
      },
      {
        path: 'history',
        name: 'History',
        component: () => import('../views/History.vue'),
        meta: { title: '历史数据' }
      },
      {
        path: 'strategy',
        name: 'Strategy',
        component: () => import('../views/Strategy.vue'),
        meta: { title: '调度策略' }
      },
      {
        path: 'alarm',
        name: 'Alarm',
        component: () => import('../views/Alarm.vue'),
        meta: { title: '告警管理' }
      },
      {
        path: 'report',
        name: 'Report',
        component: () => import('../views/Report.vue'),
        meta: { title: '统计报表' }
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('../views/User.vue'),
        meta: { title: '用户管理', admin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未登录跳转登录页
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const user = JSON.parse(localStorage.getItem('user') || 'null')

  if (to.path === '/login') {
    next()
    return
  }
  if (!token) {
    next('/login')
    return
  }
  // 管理员专属页面校验
  if (to.meta && to.meta.admin && (!user || user.role !== 'ADMIN')) {
    next('/dashboard')
    return
  }
  next()
})

export default router