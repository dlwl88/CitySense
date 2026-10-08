<template>
  <el-dialog v-model="visible" title="手机号登录" width="420px" @close="resetForm">
    <el-form label-position="top">
      <el-form-item label="手机号">
        <el-input v-model="form.phone" maxlength="11" placeholder="请输入手机号" />
      </el-form-item>
      <el-form-item label="验证码">
        <div class="code-row">
          <el-input v-model="form.code" maxlength="6" placeholder="请输入验证码" @keyup.enter="submitLogin" />
          <el-button :disabled="countdown > 0 || !form.phone" @click="sendCode">
            {{ countdown > 0 ? `${countdown}s 后重试` : '发送验证码' }}
          </el-button>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="userStore.closeLoginDialog()">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitLogin">登录</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/store/user';

const userStore = useUserStore();
const submitting = ref(false);
const countdown = ref(0);
const timer = ref(null);
const form = reactive({
  phone: '',
  code: ''
});

const visible = computed({
  get: () => userStore.loginDialogVisible,
  set: (value) => {
    if (!value) {
      userStore.closeLoginDialog();
    }
  }
});

onBeforeUnmount(() => {
  if (timer.value) {
    clearInterval(timer.value);
  }
});

async function sendCode() {
  if (!form.phone) {
    ElMessage.warning('请先输入手机号');
    return;
  }
  await userStore.sendCode(form.phone);
  ElMessage.success('验证码已发送，请查看后端日志中的模拟验证码');
  countdown.value = 60;
  timer.value = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) {
      clearInterval(timer.value);
      timer.value = null;
    }
  }, 1000);
}

async function submitLogin() {
  if (!form.phone || !form.code) {
    ElMessage.warning('请输入手机号和验证码');
    return;
  }
  submitting.value = true;
  try {
    await userStore.login({ phone: form.phone, code: form.code });
    ElMessage.success('登录成功');
    resetForm();
  } catch (error) {
    ElMessage.error(error?.message || '登录失败');
  } finally {
    submitting.value = false;
  }
}

function resetForm() {
  form.phone = '';
  form.code = '';
}
</script>

<style scoped lang="scss">
.code-row {
  display: grid;
  grid-template-columns: 1fr 124px;
  gap: 10px;
  width: 100%;
}
</style>

