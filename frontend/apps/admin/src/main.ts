import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './styles/theme.css'
import './styles/page.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
// Element Plus 全局中文语言包：日期选择器、分页等组件文本统一为标准中文
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
