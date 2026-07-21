import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import WmsSelect from './components/WmsSelect.vue'
import WmsDateText from './components/WmsDateText.vue'
import WmsResultTag from './components/WmsResultTag.vue'
import OrderStatusTag from './views/components/OrderStatusTag.vue'
import './style.css'

const app = createApp(App)
app.component('WmsSelect', WmsSelect)
app.component('WmsDateText', WmsDateText)
app.component('WmsStatusTag', OrderStatusTag)
app.component('WmsResultTag', WmsResultTag)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
