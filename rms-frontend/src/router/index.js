import { createRouter, createWebHistory } from "vue-router";
import { getCurrentUser, isAuthenticated } from "../auth/currentUser";

import DocumentsPage from "../pages/DocumentsPage.vue";
import DocumentDetailsPage from "../pages/DocumentDetailsPage.vue";
import CreateDocumentPage from "../pages/CreateDocumentPage.vue";
import InboxPage from "../pages/InboxPage.vue";
import LogsPage from "../pages/LogsPage.vue";
import UsersPage from "../pages/UsersPage.vue";
import LoginPage from "../pages/LoginPage.vue";

const routes = [
  // default
  { path: "/", redirect: "/inbox" },

  { path: "/login", component: LoginPage, meta: { public: true } },

  // ✅ New official routes: DOCUMENTS
  { path: "/documents", component: DocumentsPage },
  { path: "/documents/new", component: CreateDocumentPage },
  { path: "/documents/:id", component: DocumentDetailsPage },

  { path: "/inbox", component: InboxPage },
  { path: "/logs", component: LogsPage, meta: { logsOnly: true } },
  { path: "/users", component: UsersPage, meta: { adminOnly: true } },

  // ✅ Backward compatibility: old REPORT routes still work
  { path: "/reports", redirect: "/documents" },
  { path: "/reports/new", redirect: "/documents/new" },
  { path: "/reports/:id", redirect: (to) => `/documents/${to.params.id}` },

  // fallback
  { path: "/:pathMatch(.*)*", redirect: "/inbox" },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  if (to.meta?.public) return true;
  if (isAuthenticated()) return true;

  return {
    path: "/login",
    query: { redirect: to.fullPath },
  };
});

router.beforeEach((to) => {
  if (!to.meta?.adminOnly) return true;
  const user = getCurrentUser();
  if (user?.role === "ADMIN") return true;
  return { path: "/documents" };
});

router.beforeEach((to) => {
  if (!to.meta?.logsOnly) return true;
  const user = getCurrentUser();
  if (user?.role === "ADMIN" || user?.role === "DC") return true;
  return { path: "/documents" };
});

export default router;
