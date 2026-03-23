<template>
  <router-view />
  <ToastHost :top-offset="toastTopOffset" />
</template>

<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";
import ToastHost from "./components/ToastHost.vue";

const route = useRoute();

const toastTopOffset = computed(() => (route.path === "/login" ? 16 : 72));
</script>
