-- Add notification permission tracking to devices
ALTER TABLE "devices" ADD COLUMN "notification_permission" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "devices" ADD COLUMN "permission_updated_at" TIMESTAMP(3);
