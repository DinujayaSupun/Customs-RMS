// Shared logic for the Groups feature (GroupsPage.vue, and the forward-to-group pickers in
// DocumentDetailsPage.vue and InboxPage.vue), extracted so it has one tested implementation
// instead of three copies.

export function initialsFor(name) {
  const text = String(name || "").trim();
  if (!text) return "G";
  const parts = text.split(/\s+/).slice(0, 2);
  return parts.map((p) => p[0]?.toUpperCase() || "").join("") || "G";
}

export function filterGroupsBySearch(groups, term) {
  const list = Array.isArray(groups) ? groups : [];
  const q = String(term || "").trim().toLowerCase();
  if (!q) return list;

  return list.filter((g) => {
    const searchableText = [g?.name, g?.id, ...((g?.members || []).map((m) => m?.fullName))]
      .filter((part) => part !== null && part !== undefined && String(part).trim() !== "")
      .join(" ")
      .toLowerCase();

    return searchableText.includes(q);
  });
}

// De-duplicates by id (a defensive guarantee independent of whatever picker component supplied
// selectedMemberIds) and resolves each member's admin flag.
export function buildGroupMembersPayload(selectedMemberIds, memberAdminFlags) {
  const flags = memberAdminFlags || {};
  const seen = new Set();
  const result = [];
  for (const rawId of selectedMemberIds || []) {
    const id = String(rawId);
    if (seen.has(id)) continue;
    seen.add(id);
    result.push({ userId: Number(id), isAdmin: Boolean(flags[id]) });
  }
  return result;
}

export function countAdmins(selectedMemberIds, memberAdminFlags) {
  return buildGroupMembersPayload(selectedMemberIds, memberAdminFlags)
    .filter((m) => m.isAdmin).length;
}

export function buildForwardPayload({ mode, toUserId, toGroupId, ccUserIds, bccUserIds, forwardVisibility, remarkText }) {
  const target = mode === "group" ? { toGroupId: Number(toGroupId) } : { toUserId: Number(toUserId) };
  return {
    ...target,
    ccUserIds: ccUserIds || [],
    bccUserIds: bccUserIds || [],
    forwardVisibility,
    remarkText: remarkText ?? null,
  };
}
