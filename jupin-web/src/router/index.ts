import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/login',
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/auth/Login.vue'),
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/auth/Register.vue'),
  },
  {
    path: '/player',
    component: () => import('../layouts/PlayerLayout.vue'),
    meta: { role: 0 },
    children: [
      { path: '', redirect: '/player/pools' },
      {
        path: 'pools',
        name: 'PlayerPools',
        component: () => import('../views/player/PoolList.vue'),
      },
      {
        path: 'pools/create',
        name: 'PlayerPoolCreate',
        component: () => import('../views/player/PoolCreate.vue'),
      },
      {
        path: 'pools/:id',
        name: 'PlayerPoolDetail',
        component: () => import('../views/player/PoolDetail.vue'),
      },
      {
        path: 'shops',
        name: 'PlayerShops',
        component: () => import('../views/player/ShopList.vue'),
      },
      {
        path: 'shops/:id',
        name: 'PlayerShopDetail',
        component: () => import('../views/player/ShopDetail.vue'),
      },
      {
        path: 'my',
        name: 'PlayerMy',
        component: () => import('../views/player/MyPage.vue'),
      },
    ],
  },
  {
    path: '/shop',
    component: () => import('../layouts/ShopLayout.vue'),
    meta: { role: 1 },
    children: [
      { path: '', redirect: '/shop/dashboard' },
      {
        path: 'dashboard',
        name: 'ShopDashboard',
        component: () => import('../views/shop/Dashboard.vue'),
      },
      {
        path: 'scripts',
        name: 'ShopScripts',
        component: () => import('../views/shop/Scripts.vue'),
      },
      {
        path: 'pools/create',
        name: 'ShopPoolCreate',
        component: () => import('../views/shop/PoolCreate.vue'),
      },
      {
        path: 'pools/:id',
        name: 'ShopPoolDetail',
        component: () => import('../views/shop/PoolDetail.vue'),
      },
      {
        path: 'members',
        name: 'ShopMembers',
        component: () => import('../views/shop/Members.vue'),
      },
      {
        path: 'info',
        name: 'ShopInfo',
        component: () => import('../views/shop/ShopInfo.vue'),
      },
    ],
  },
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    meta: { role: 2 },
    children: [
      { path: '', redirect: '/admin/users' },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('../views/admin/UserManage.vue'),
      },
      {
        path: 'scripts',
        name: 'AdminScripts',
        component: () => import('../views/admin/ScriptManage.vue'),
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  auth.init()

  const publicPages = ['/login', '/register']
  if (publicPages.includes(to.path)) {
    next()
    return
  }

  if (!auth.isLoggedIn) {
    next('/login')
    return
  }

  const requiredRole = to.meta.role as number | undefined
  if (requiredRole !== undefined && auth.role !== requiredRole) {
    const roleMap: Record<number, string> = { 0: '/player', 1: '/shop', 2: '/admin' }
    next(roleMap[auth.role ?? 0] || '/login')
    return
  }

  next()
})

export default router
