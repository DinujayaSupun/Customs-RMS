<template>
  <div class="recipientChipPicker" :class="{ disabled }">
    <div class="selectedChips">
      <span v-for="id in normalizedValue" :key="id" class="recipientChip">
        {{ labelForId(id) }}
        <button type="button" :disabled="disabled" @click="removeId(id)" aria-label="Remove recipient">x</button>
      </span>
      <span v-if="normalizedValue.length === 0" class="pickerPlaceholder">{{ placeholder }}</span>
    </div>
    <input
      v-model="search"
      class="recipientChipSearch"
      :disabled="disabled"
      :placeholder="searchPlaceholder"
      @focus="focused = true"
      @blur="handleBlur"
    />
    <div v-if="showOptions" class="recipientChipOptions">
      <button
        v-for="user in filteredOptions"
        :key="user.id"
        type="button"
        class="recipientChipOption"
        @mousedown.prevent="addUser(user)"
      >
        {{ formatUserLabel(user) }}
      </button>
      <div v-if="filteredOptions.length === 0" class="recipientChipEmpty">No users found.</div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";
import { formatUserLabel } from "../auth/userLabel";

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  users: { type: Array, default: () => [] },
  excludeUserIds: { type: Array, default: () => [] },
  otherSelectedIds: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: "No copied recipients selected" },
  searchPlaceholder: { type: String, default: "Search users..." },
});

const emit = defineEmits(["update:modelValue"]);

const search = ref("");
const focused = ref(false);

const normalizedValue = computed(() => normalizeIds(props.modelValue));
const blockedIds = computed(() => new Set([
  ...normalizeIds(props.excludeUserIds),
  ...normalizeIds(props.otherSelectedIds),
  ...normalizedValue.value,
]));

const userById = computed(() => {
  const map = new Map();
  for (const user of props.users) {
    map.set(String(user.id), user);
  }
  return map;
});

const filteredOptions = computed(() => {
  const term = search.value.trim().toLowerCase();
  return props.users
    .filter((user) => !blockedIds.value.has(String(user.id)))
    .filter((user) => {
      if (!term) return true;
      return [
        formatUserLabel(user),
        user.username,
        user.fullName,
        user.name,
        user.role,
        user.department,
      ].filter(Boolean).join(" ").toLowerCase().includes(term);
    })
    .slice(0, 12);
});

const showOptions = computed(() => focused.value && !props.disabled);

function normalizeIds(ids) {
  return Array.from(new Set((ids || [])
    .map((id) => String(id))
    .filter((id) => id && id !== "null" && id !== "undefined")));
}

function labelForId(id) {
  return userById.value.has(String(id)) ? formatUserLabel(userById.value.get(String(id))) : `User ${id}`;
}

function addUser(user) {
  if (!user?.id || blockedIds.value.has(String(user.id))) return;
  emit("update:modelValue", [...normalizedValue.value, String(user.id)]);
  search.value = "";
  focused.value = true;
}

function removeId(id) {
  emit("update:modelValue", normalizedValue.value.filter((selectedId) => selectedId !== String(id)));
}

function handleBlur() {
  window.setTimeout(() => {
    focused.value = false;
  }, 120);
}
</script>

<style scoped>
.recipientChipPicker {
  position: relative;
  display: grid;
  gap: 8px;
}

.selectedChips {
  min-height: 42px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px;
  border: 1px solid #d8e0ec;
  border-radius: 10px;
  background: #f8fafc;
}

.recipientChip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 5px 8px;
  border-radius: 999px;
  background: #e8f0ff;
  color: #173b7a;
  font-size: 12px;
  font-weight: 700;
}

.recipientChip button {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-weight: 900;
}

.pickerPlaceholder {
  color: #64748b;
  font-size: 13px;
}

.recipientChipSearch {
  width: 100%;
  min-height: 38px;
  border: 1px solid #d8e0ec;
  border-radius: 10px;
  padding: 8px 10px;
  outline: none;
}

.recipientChipSearch:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.recipientChipOptions {
  position: absolute;
  z-index: 30;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  max-height: 220px;
  overflow: auto;
  border: 1px solid #d8e0ec;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.16);
}

.recipientChipOption {
  width: 100%;
  border: 0;
  background: transparent;
  padding: 9px 10px;
  text-align: left;
  cursor: pointer;
}

.recipientChipOption:hover {
  background: #eff6ff;
}

.recipientChipEmpty {
  padding: 10px;
  color: #64748b;
  font-size: 13px;
}
</style>
