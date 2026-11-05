import { createApp } from 'vue'
import { createPinia } from 'pinia'
import naive from 'naive-ui'

import App from './App.vue'
import router from './router'
import { installGlobalSpark } from './utils/spark'
import './styles/global.css'

/**
 * 应用入口
 *
 * <p>初始化顺序说明：
 * <ol>
 *   <li><b>Pinia</b>：状态管理，必须在 router 之前注册。
 *       因为路由守卫里要读取用户状态来判断能否进入某页面，
 *       如果 Pinia 还没装好，守卫里拿到的 store 就是 undefined。</li>
 *   <li><b>Router</b>：路由与权限守卫。</li>
 *   <li><b>Naive UI</b>：全局注册组件，页面里直接用 n-button 等，
 *       省去每个页面重复 import。</li>
 * </ol>
 */
const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(naive)

/** 全局点击火花：页面内任意位置（含背景空白处）点击即触发，一次监听全局生效 */
installGlobalSpark()

app.mount('#app')
