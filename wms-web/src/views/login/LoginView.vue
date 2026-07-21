<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const submitting = ref(false)
const captchaImage = ref('')
const captchaLoading = ref(false)

const form = reactive({
  username: 'admin',
  password: '123456',
  captcha: '',
  captchaKey: '',
})

async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    const data = await getCaptcha()
    form.captchaKey = data.captchaKey
    captchaImage.value = data.captchaImage.startsWith('data:')
      ? data.captchaImage
      : `data:image/png;base64,${data.captchaImage}`
    form.captcha = ''
  } catch {
    captchaImage.value = ''
  } finally {
    captchaLoading.value = false
  }
}

async function handleLogin() {
  if (submitting.value) return
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!form.password) {
    ElMessage.warning('请输入密码')
    return
  }
  if (!form.captcha.trim()) {
    ElMessage.warning('请输入验证码')
    return
  }

  submitting.value = true
  loading.value = true
  try {
    localStorage.removeItem('wms_token')
    localStorage.removeItem('wms_refresh_token')
    await userStore.login(form)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (err) {
    const msg = err instanceof Error ? err.message : '登录失败'
    if (msg && msg !== 'Network Error') {
      ElMessage.error(msg)
    }
    await refreshCaptcha()
  } finally {
    loading.value = false
    submitting.value = false
  }
}

onMounted(refreshCaptcha)
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="logo">WMS</div>
        <h1>仓储管理系统</h1>
        <p class="subtitle">Web 管理后台 · SQL Server</p>
      </div>

      <el-form :model="form" label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="用户名">
          <el-input
            v-model="form.username"
            placeholder="工号 / 用户名"
            size="large"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="登录密码"
            size="large"
          />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="captcha-row">
            <el-input
              v-model="form.captcha"
              placeholder="右侧验证码"
              size="large"
              maxlength="6"
            />
            <div class="captcha-box" @click="refreshCaptcha">
              <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
              <span v-else-if="captchaLoading">加载中...</span>
              <span v-else>点击刷新</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" native-type="submit" size="large" style="width: 100%">
            登 录
          </el-button>
        </el-form-item>
      </el-form>

    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e40af 0%, #0f172a 55%, #1e293b 100%);
  padding: 24px;
}
.login-card {
  width: 100%;
  max-width: 420px;
  padding: 40px 36px 32px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.35);
}
.brand {
  text-align: center;
  margin-bottom: 28px;
}
.logo {
  width: 56px;
  height: 56px;
  margin: 0 auto 12px;
  border-radius: 14px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  color: #fff;
  font-weight: 700;
  font-size: 18px;
  line-height: 56px;
}
h1 {
  margin: 0;
  font-size: 22px;
  color: #0f172a;
}
.subtitle {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
}
.captcha-row {
  display: flex;
  gap: 12px;
  width: 100%;
}
.captcha-box {
  flex-shrink: 0;
  width: 120px;
  height: 40px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  color: #94a3b8;
  font-size: 12px;
}
.captcha-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.hint {
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
  margin: 4px 0 0;
}
:deep(.el-form-item__label) {
  font-weight: 500;
  color: #334155;
}
</style>
