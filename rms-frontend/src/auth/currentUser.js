const USERS = [
  { id: 6, name: "Director Customs", role: "DC" },
  { id: 7, name: "Deputy Director Customs", role: "DDC" },
  { id: 8, name: "Senior Superintendent", role: "SC" },
  { id: 9, name: "Assistant Superintendent", role: "ASC" },
  { id: 10, name: "Personal Management Assistant", role: "PMA" },
];

export function getUsers() {
  return USERS;
}

export function getCurrentUser() {
  const raw = localStorage.getItem("rms_user");
  if (!raw) return USERS[0];

  try {
    const parsed = JSON.parse(raw);

    // ✅ If old stored user id doesn't exist anymore (like id=5), reset safely
    const match = USERS.find((u) => u.id === parsed?.id);
    return match || USERS[0];
  } catch {
    return USERS[0];
  }
}

export function setCurrentUser(user) {
  localStorage.setItem("rms_user", JSON.stringify(user));
  window.dispatchEvent(new Event("rms_user_changed"));
}

export function clearCurrentUser() {
  localStorage.removeItem("rms_user");
  window.dispatchEvent(new Event("rms_user_changed"));
}