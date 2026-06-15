import { createRouter, createWebHistory } from "vue-router";
import { getCurrentUser, hasPermission, clearSession, getAccessToken } from "../auth/currentUser";
import { getMe } from "../api/auth.api";
import { shouldClearSessionForAuthCheckError } from "./authGuardLogic";
import { markAuthValidated, resetAuthValidation, shouldValidateAuth } from "./authValidationCache";
import DocumentsPage from "../pages/DocumentsPage.vue";
import DocumentDetailsPage from "../pages/DocumentDetailsPage.vue";
import CreateDocumentPage from "../pages/CreateDocumentPage.vue";
import InboxPage from "../pages/InboxPage.vue";
import LogsPage from "../pages/LogsPage.vue";
import PermissionsPage from "../pages/PermissionsPage.vue";
import ProfilePage from "../pages/ProfilePage.vue";
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
  { path: "/profile", component: ProfilePage },
  { path: "/logs", component: LogsPage, meta: { requiredPermission: "VIEW_LOGS" } },
  { path: "/users", component: UsersPage, meta: { adminOnly: true } },
  { path: "/permissions", component: PermissionsPage, meta: { adminOnly: true } },

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

// Validate the token with the backend before showing protected pages; stale tokens are cleared here.
router.beforeEach(async (to) => {
  if (to.meta?.public) return true;

  const token = getAccessToken();

  if (!token) {
    return {
      path: "/login",
      query: { redirect: to.fullPath },
    };
  }

  if (!shouldValidateAuth()) {
    return true;
  }

  try {
    await getMe();
    markAuthValidated();
    return true;
  } catch (error) {
    if (!shouldClearSessionForAuthCheckError(error)) {
      return true;
    }

    clearSession();
    resetAuthValidation();
    return {
      path: "/login",
      query: { redirect: to.fullPath },
    };
  }
});

// Admin pages are guarded in the router as well as on the backend to avoid exposing admin UI.
router.beforeEach((to) => {
  if (!to.meta?.adminOnly) return true;
  const user = getCurrentUser();
  if (user?.role === "ADMIN") return true;
  return { path: "/documents" };
});

// Permission-specific routes stay declarative through route meta.
router.beforeEach((to) => {
  if (!to.meta?.requiredPermission) return true;
  const user = getCurrentUser();
  if (hasPermission(user, to.meta.requiredPermission)) return true;
  return { path: "/documents" };
});

export default router;
