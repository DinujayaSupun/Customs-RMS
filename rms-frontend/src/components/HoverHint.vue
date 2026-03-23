<template>
  <span class="hintWrap" tabindex="0" :aria-label="text">
    <span class="hintIcon" aria-hidden="true">i</span>
    <span class="hintBubble">{{ text }}</span>
  </span>
</template>

<script setup>
defineProps({
  text: {
    type: String,
    required: true,
  },
});
</script>

<style scoped>
.hintWrap {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 999px;
  border: 1px solid #94a3b8;
  background: #f8fafc;
  color: #334155;
  font-size: 11px;
  font-weight: 700;
  cursor: help;
}

.hintBubble {
  position: absolute;
  left: 0;
  bottom: calc(100% + 8px);
  transform: none;
  min-width: 220px;
  max-width: 320px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  background: #0f172a;
  color: #f8fafc;
  font-size: 12px;
  line-height: 1.35;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.25);
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transition: opacity 0.15s ease;
  z-index: 40;
}

.hintWrap:hover .hintBubble,
.hintWrap:focus-visible .hintBubble {
  opacity: 1;
  visibility: visible;
}
</style>
