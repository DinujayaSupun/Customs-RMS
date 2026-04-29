import { createAuthedHttp, getApiErrorMessage } from "./apiClient";

const http = createAuthedHttp();

function getMsg(e) {
  return getApiErrorMessage(e);
}

export async function listAuditLogs(params = {}) {
  try {
    return (await http.get("/audit-logs", { params })).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function getAuditLogFilterOptions() {
  try {
    return (await http.get("/audit-logs/filter-options")).data;
  } catch (e) {
    throw new Error(getMsg(e));
  }
}

export async function exportAuditLogsCsv(params = {}) {
  try {
    const response = await http.get("/audit-logs/export", {
      params,
      responseType: "blob",
    });

    const disposition = response.headers?.["content-disposition"] || "";
    const match = disposition.match(/filename="?([^"]+)"?/i);
    const fileName = match?.[1] || "audit-logs.csv";

    return {
      blob: response.data,
      fileName,
    };
  } catch (e) {
    throw new Error(getMsg(e));
  }
}
