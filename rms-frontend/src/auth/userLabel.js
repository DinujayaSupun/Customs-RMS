export function formatUserLabel(user) {
  if (!user) return "-";
  const name = user.fullName || user.name || `ID ${user.id}`;
  const role = user.role || "-";
  return `${name} • ${role} • ID ${user.id}`;
}

export function formatUserLabelById(userId, users = []) {
  const match = users.find((u) => Number(u.id) === Number(userId));
  if (!match) return `ID ${userId}`;
  return formatUserLabel(match);
}
