export function formatUserLabel(user) {
  if (!user) return "-";
  const name = String(user.fullName || user.name || user.username || "").trim();
  const role = String(user.role || "").trim();
  if (name && role) return `${name} (${role})`;
  if (name) return name;
  if (role) return `Unknown user (${role})`;
  return "Unknown user";
}

export function formatUserLabelById(userId, users = []) {
  const match = users.find((u) => Number(u.id) === Number(userId));
  if (!match) return "Unknown user";
  return formatUserLabel(match);
}

export function formatUserLabelFromParts({ userId, name, role } = {}, users = []) {
  const fullName = String(name || "").trim();
  const roleName = String(role || "").trim();
  if (fullName || roleName) {
    return formatUserLabel({ fullName, role: roleName });
  }
  if (userId === null || userId === undefined || userId === "") return "-";
  return formatUserLabelById(userId, users);
}
