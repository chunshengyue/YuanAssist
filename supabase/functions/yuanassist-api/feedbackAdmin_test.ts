import {
  assertFeedbackAdminDevice,
  FEEDBACK_ADMIN_DEVICE_ID,
  normalizeAdminFeedbackReply,
} from "./feedbackAdmin.ts";

function assertThrows(fn: () => unknown, expectedMessage: string) {
  try {
    fn();
  } catch (error) {
    if (error instanceof Error && error.message === expectedMessage) {
      return;
    }
    throw error;
  }
  throw new Error("Expected function to throw");
}

Deno.test("admin device id passes validation", () => {
  assertFeedbackAdminDevice(FEEDBACK_ADMIN_DEVICE_ID);
});

Deno.test("empty reply is rejected with reply 不能为空", () => {
  assertThrows(() => normalizeAdminFeedbackReply("   "), "reply 不能为空");
});

Deno.test("non admin device is rejected with 当前设备无反馈管理权限", () => {
  assertThrows(
    () => assertFeedbackAdminDevice("not-admin-device"),
    "当前设备无反馈管理权限",
  );
});
