import { createRouter, createWebHistory } from "vue-router";
import { isAuthenticated } from "../auth/currentUser";

import DocumentsPage from "../pages/DocumentsPage.vue";
import DocumentDetailsPage from "../pages/DocumentDetailsPage.vue";
import CreateDocumentPage from "../pages/CreateDocumentPage.vue";
import InboxPage from "../pages/InboxPage.vue";
import LogsPage from "../pages/LogsPage.vue";
import UsersPage from "../pages/UsersPage.vue";
import LoginPage from "../pages/LoginPage.vue";

const routes = [
  // default
  { path: "/", redirect: "/documents" },

  { path: "/login", component: LoginPage, meta: { public: true } },

  // ✅ New official routes: DOCUMENTS
  { path: "/documents", component: DocumentsPage },
  { path: "/documents/new", component: CreateDocumentPage },
  { path: "/documents/:id", component: DocumentDetailsPage },

  { path: "/inbox", component: InboxPage },
  { path: "/logs", component: LogsPage },
  { path: "/users", component: UsersPage },

  // ✅ Backward compatibility: old REPORT routes still work
  { path: "/reports", redirect: "/documents" },
  { path: "/reports/new", redirect: "/documents/new" },
  { path: "/reports/:id", redirect: (to) => `/documents/${to.params.id}` },

  // fallback
  { path: "/:pathMatch(.*)*", redirect: "/documents" },
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

export default router;
