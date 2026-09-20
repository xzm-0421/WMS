<template>
  <view class="field">
    <input
      class="input"
      :value="modelValue"
      :password="isPassword && !visible"
      :placeholder="placeholder"
      :maxlength="maxlength"
      placeholder-class="ph"
      @input="onInput"
    />
    <view v-if="showEye" class="eye" @click="visible = !visible">
      <view class="eye-outer"></view>
      <view v-if="!visible" class="eye-slash"></view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  isPassword: { type: Boolean, default: false },
  showEye: { type: Boolean, default: false },
  maxlength: { type: Number, default: 64 },
})

const emit = defineEmits(['update:modelValue'])
const visible = ref(false)

function onInput(e) {
  emit('update:modelValue', e.detail.value)
}
</script>

<style scoped>
.field {
  height: 96rpx;
  background: #eef2fb;
  border-radius: 48rpx;
  display: flex;
  align-items: center;
  padding: 0 36rpx;
  margin-bottom: 28rpx;
}
.input {
  flex: 1;
  height: 96rpx;
  font-size: 28rpx;
  color: #2c3158;
}
.ph {
  color: #b0b4c8;
}
.eye {
  width: 48rpx;
  height: 48rpx;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}
.eye-outer {
  width: 32rpx;
  height: 20rpx;
  border: 3rpx solid #c5c8d6;
  border-radius: 12rpx / 10rpx;
  box-sizing: border-box;
  position: relative;
}
.eye-outer::after {
  content: '';
  position: absolute;
  left: 8rpx;
  top: 3rpx;
  width: 10rpx;
  height: 10rpx;
  border: 3rpx solid #c5c8d6;
  border-radius: 50%;
  box-sizing: border-box;
}
.eye-slash {
  position: absolute;
  width: 36rpx;
  height: 3rpx;
  background: #c5c8d6;
  transform: rotate(-28deg);
}
</style>
