import { createApiError, createAuthedHttp } from "./apiClient";

const http = createAuthedHttp();

function getMsg(e) {
  return createApiError(e, { includeDetails: true });
}

export async function login(username, password) {
  try {
    return (await http.post("/auth/login", { username, password })).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function getMe() {
  try {
    return (await http.get("/auth/me")).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function updateMe(payload) {
  try {
    return (await http.put("/auth/me", payload)).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function changeMyPassword(payload) {
  try {
    await http.patch("/auth/me/password", payload);
  } catch (e) {
    throw getMsg(e);
  }
}

export async function uploadMyProfilePicture(file) {
  try {
    const form = new FormData();
    form.append("file", file);
    return (await http.post("/auth/me/profile-picture", form, {
      headers: { "Content-Type": "multipart/form-data" },
    })).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function createMyProfilePictureUrl(version) {
  try {
    const data = (await http.post("/auth/me/profile-picture-token")).data;
    const rawUrl = String(data?.url || "");
    if (!rawUrl) return "";

    const url = new URL(rawUrl);
    if (version) url.searchParams.set("v", String(version));
    return url.toString();
  } catch (e) {
    throw getMsg(e);
  }
}

export async function listUsers() {
  try {
    return (await http.get("/auth/users")).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminListUsers(params = {}) {
  try {
    return (await http.get("/admin/users", { params })).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminListRoles() {
  try {
    return (await http.get("/admin/users/roles")).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminCreateUser(payload) {
  try {
    return (await http.post("/admin/users", payload)).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminUpdateUser(userId, payload) {
  try {
    return (await http.put(`/admin/users/${userId}`, payload)).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminActivateUser(userId) {
  try {
    return (await http.patch(`/admin/users/${userId}/activate`)).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminDeactivateUser(userId, fallbackDcUserId) {
  try {
    return (await http.patch(`/admin/users/${userId}/deactivate`, { fallbackDcUserId })).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminBulkDeactivateUsers(userIds, fallbackDcUserId) {
  try {
    await http.post("/admin/users/bulk-deactivate", { userIds, fallbackDcUserId });
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminResetPassword(userId, newPassword) {
  try {
    await http.patch(`/admin/users/${userId}/reset-password`, { newPassword });
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminGetUserPermissions(userId) {
  try {
    return (await http.get(`/admin/users/${userId}/permissions`)).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminUpdateUserPermissions(userId, entries) {
  try {
    return (await http.put(`/admin/users/${userId}/permissions`, { entries })).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminDeleteUser(userId) {
  try {
    await http.delete(`/admin/users/${userId}`);
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminBulkDeleteUsers(userIds) {
  try {
    await http.post("/admin/users/bulk-delete", { userIds });
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminListDuplicateUsers() {
  try {
    return (await http.get("/admin/users/duplicates")).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminMergeUsers(sourceUserId, targetUserId) {
  try {
    await http.post("/admin/users/merge", { sourceUserId, targetUserId });
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminGetPermissionsMatrix() {
  try {
    return (await http.get("/admin/permissions")).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminUpdatePermissionsMatrix(entries) {
  try {
    return (await http.put("/admin/permissions", { entries })).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminGetDcAutoForwardConfig() {
  try {
    return (await http.get("/admin/permissions/dc-auto-forward")).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminUpdateDcAutoForwardConfig(payload) {
  try {
    return (await http.put("/admin/permissions/dc-auto-forward", payload)).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function adminSavePermissionsPage(payload) {
  try {
    return (await http.put("/admin/permissions/page", payload)).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function listGroups() {
  try {
    return (await http.get("/groups")).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function getGroupDocuments(groupId) {
  try {
    return (await http.get(`/groups/${groupId}/documents`)).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function createGroup(payload) {
  try {
    return (await http.post("/groups", payload)).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function updateGroup(groupId, payload) {
  try {
    return (await http.put(`/groups/${groupId}`, payload)).data;
  } catch (e) {
    throw getMsg(e);
  }
}

export async function deleteGroup(groupId) {
  try {
    await http.delete(`/groups/${groupId}`);
  } catch (e) {
    throw getMsg(e);
  }
}
