export const FEEDBACK_ADMIN_DEVICE_ID = "815e9c7c33fa662e";

export function isFeedbackAdminDeviceId(deviceId: string): boolean {
  return deviceId.trim() === FEEDBACK_ADMIN_DEVICE_ID;
}

export function assertFeedbackAdminDevice(deviceId: string): void {
  if (!isFeedbackAdminDeviceId(deviceId)) {
    throw new Error("当前设备无反馈管理权限");
  }
}

export function normalizeAdminFeedbackReply(value: unknown): string {
  if (typeof value !== "string") {
    throw new Error("reply 不能为空");
  }
  const reply = value.trim();
  if (!reply) {
    throw new Error("reply 不能为空");
  }
  return reply;
}
