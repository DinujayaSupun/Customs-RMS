export function getWorkflowSenderSuccessMessage(actionType) {
  const action = String(actionType || "").trim().toUpperCase();
  if (action === "FORWARD") return "Document sent successfully.";
  if (action === "RETURN") return "Document returned successfully.";
  return "Document action completed successfully.";
}
