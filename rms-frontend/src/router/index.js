import { createRouter, createWebHistory } from "vue-router";

import DocumentsPage from "../pages/DocumentsPage.vue";
import DocumentDetailsPage from "../pages/DocumentDetailsPage.vue";
import CreateDocumentPage from "../pages/CreateDocumentPage.vue";
import InboxPage from "../pages/InboxPage.vue";
import LogsPage from "../pages/LogsPage.vue";
import UsersPage from "../pages/UsersPage.vue";

const routes = [
  // default
  { path: "/", redirect: "/documents" },

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

export default createRouter({
  history: createWebHistory(),
  routes,
});
