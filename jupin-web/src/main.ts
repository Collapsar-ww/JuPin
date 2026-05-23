import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// @ts-expect-error element-plus locale lacks types
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import { useAuthStore } from './stores/auth'
import './style.css'

const app = createApp(App)
app.use(ElementPlus, { locale: zhCn })
app.use(router)
app.use(createPinia())

const auth = useAuthStore()
auth.init()

app.mount('#app')
