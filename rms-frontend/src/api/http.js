export async function apiGet(path) {
  const res = await fetch(`/api${path}`);
  const data = await res.json().catch(() => null);
  if (!res.ok) throw data ?? { message: "Request failed" };
  return data;
}

export async function apiPost(path, body) {
  const res = await fetch(`/api${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body ?? {}),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) throw data ?? { message: "Request failed" };
  return data;
}
