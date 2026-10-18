<script setup lang="ts">
// 用户协议与隐私政策占位页（二期填充正式文案）
</script>

<template>
  <view class="agreement">
    <view class="card">
      <view class="title">用户协议与隐私政策</view>
      <view class="para">本页面为协议占位页，正式的用户协议与隐私政策文案将在二期上线前填充并生效。</view>
      <view class="para">
        我们在此说明：本系统在您使用过程中将收集手机号、定位信息（用于到场签到）、您主动上传的现场照片等信息，仅用于吊装业务派单、考勤与工单管理目的，不会用于其他用途。
      </view>
      <view class="para">如有疑问，请联系管理员。</view>
    </view>
  </view>
</template>

<style scoped>
.agreement {
  padding: 16px;
}
.card {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 16px;
}
.title {
  font-size: 17px;
  font-weight: 600;
  margin-bottom: 12px;
}
.para {
  font-size: 15px;
  color: var(--txt);
  margin-bottom: 12px;
}
</style>
