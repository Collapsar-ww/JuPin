import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '../api/auth'
import * as authApi from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)
  const accessToken = ref<string | null>(null)
  const refreshToken = ref<string | null>(null)

  const isLoggedIn = computed(() => !!accessToken.value)
  const role = computed(() => user.value?.role ?? null)

  function init() {
    const savedUser = localStorage.getItem('userInfo')
    const savedAccess = localStorage.getItem('accessToken')
    const savedRefresh = localStorage.getItem('refreshToken')
    if (savedUser && savedAccess) {
      user.value = JSON.parse(savedUser)
      accessToken.value = savedAccess
      refreshToken.value = savedRefresh
    }
  }

  function setLogin(data: authApi.LoginResult) {
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    user.value = data.user
    localStorage.setItem('accessToken', data.accessToken)
    localStorage.setItem('refreshToken', data.refreshToken)
    localStorage.setItem('userInfo', JSON.stringify(data.user))
  }

  function logout() {
    user.value = null
    accessToken.value = null
    refreshToken.value = null
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userInfo')
  }

  return { user, accessToken, refreshToken, isLoggedIn, role, init, setLogin, logout }
})
