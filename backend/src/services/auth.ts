import { prisma } from '../db.js';
import { config, isDev } from '../config.js';
import {
  hashPhone,
  generateOtp,
  hashOtp,
  generateDeviceId,
} from '../utils/crypto.js';
import { signJwt } from '../utils/jwt.js';
import { whatsappService } from './whatsapp.js';

const OTP_EXPIRY_MINUTES = 10;
const MAX_OTP_ATTEMPTS = 5;

interface OtpResult {
  success: boolean;
  message: string;
  expiresAt?: Date;
}

interface VerifyResult {
  success: boolean;
  message: string;
  token?: string;
  userId?: string;
  deviceId?: string;
  isNewUser?: boolean;
}

export class AuthService {
  /**
   * Request OTP for phone number.
   */
  async requestOtp(phone: string, countryCode: string = 'IN'): Promise<OtpResult> {
    const phoneHash = hashPhone(phone);
    const otp = generateOtp();
    const otpHashed = hashOtp(otp);

    const expiresAt = new Date();
    expiresAt.setMinutes(expiresAt.getMinutes() + OTP_EXPIRY_MINUTES);

    // Find existing user
    const existingUser = await prisma.user.findUnique({
      where: { phoneHash },
    });

    // Create OTP request
    await prisma.otpRequest.create({
      data: {
        userId: existingUser?.id,
        phoneHash,
        otpHash: otpHashed,
        expiresAt,
      },
    });

    // Send OTP
    await this.sendOtp(phone, otp);

    return {
      success: true,
      message: 'OTP sent successfully',
      expiresAt,
    };
  }

  /**
   * Verify OTP and create/login user.
   */
  async verifyOtp(
    phone: string,
    otp: string,
    deviceInfo?: {
      platform?: string;
      appVersion?: string;
      osVersion?: string;
    }
  ): Promise<VerifyResult> {
    const phoneHash = hashPhone(phone);
    const otpHashed = hashOtp(otp);

    // Find valid OTP request
    const otpRequest = await prisma.otpRequest.findFirst({
      where: {
        phoneHash,
        expiresAt: { gt: new Date() },
        verified: false,
        attempts: { lt: MAX_OTP_ATTEMPTS },
      },
      orderBy: { createdAt: 'desc' },
    });

    if (!otpRequest) {
      return {
        success: false,
        message: 'OTP expired or not found. Please request a new OTP.',
      };
    }

    // Increment attempts
    await prisma.otpRequest.update({
      where: { id: otpRequest.id },
      data: { attempts: { increment: 1 } },
    });

    // Verify OTP
    if (otpRequest.otpHash !== otpHashed) {
      const remaining = MAX_OTP_ATTEMPTS - otpRequest.attempts - 1;
      return {
        success: false,
        message: remaining > 0
          ? `Invalid OTP. ${remaining} attempts remaining.`
          : 'Too many invalid attempts. Please request a new OTP.',
      };
    }

    // Mark OTP as verified
    await prisma.otpRequest.update({
      where: { id: otpRequest.id },
      data: { verified: true },
    });

    // Find or create user
    let user = await prisma.user.findUnique({
      where: { phoneHash },
    });

    const isNewUser = !user;

    if (!user) {
      user = await prisma.user.create({
        data: {
          phoneHash,
          country: 'IN',
          timezone: 'Asia/Kolkata',
          consent: {
            create: {
              cloudAiEnabled: false,
              uploadRawEnabled: false,
              whatsappEnabled: false,
            },
          },
        },
      });
    }

    // Create device
    const device = await prisma.device.create({
      data: {
        userId: user.id,
        platform: deviceInfo?.platform ?? 'android',
        appVersion: deviceInfo?.appVersion,
        osVersion: deviceInfo?.osVersion,
      },
    });

    // Generate JWT
    const token = signJwt(user.id, device.id);

    return {
      success: true,
      message: isNewUser ? 'Account created successfully' : 'Login successful',
      token,
      userId: user.id,
      deviceId: device.id,
      isNewUser,
    };
  }

  /**
   * Send OTP via configured provider.
   */
  private async sendOtp(phone: string, otp: string): Promise<void> {
    const provider = config.OTP_PROVIDER;

    switch (provider) {
      case 'console':
        console.log(`[OTP] ${phone}: ${otp}`);
        break;

      case 'whatsapp':
        const result = await whatsappService.sendOtp(phone, otp);
        if (!result.success) {
          // Fallback to console in dev mode
          if (isDev) {
            console.log(`[OTP Fallback] ${phone}: ${otp}`);
            return;
          }
          throw new Error(`WhatsApp OTP failed: ${result.error}`);
        }
        break;

      case 'twilio':
        await this.sendOtpTwilio(phone, otp);
        break;

      default:
        // Default to console logging
        console.log(`[OTP] ${phone}: ${otp}`);
    }
  }

  private async sendOtpTwilio(phone: string, otp: string): Promise<void> {
    // Implement Twilio SMS sending if needed
    throw new Error('Twilio not configured');
  }

  /**
   * Update device last seen.
   */
  async updateDeviceLastSeen(deviceId: string): Promise<void> {
    await prisma.device.update({
      where: { id: deviceId },
      data: { lastSeenAt: new Date() },
    });
  }

  /**
   * Delete user and all associated data.
   */
  async deleteUser(userId: string): Promise<void> {
    // Soft delete - keep record but remove PII
    await prisma.$transaction([
      // Delete events
      prisma.event.deleteMany({ where: { userId } }),
      // Delete transactions
      prisma.transaction.deleteMany({ where: { userId } }),
      // Delete summaries
      prisma.weeklySummary.deleteMany({ where: { userId } }),
      // Delete WhatsApp logs
      prisma.whatsappSendLog.deleteMany({ where: { userId } }),
      // Delete devices
      prisma.device.deleteMany({ where: { userId } }),
      // Delete consent
      prisma.consent.deleteMany({ where: { userId } }),
      // Delete OTP requests
      prisma.otpRequest.deleteMany({ where: { userId } }),
      // Soft delete user
      prisma.user.update({
        where: { id: userId },
        data: {
          deletedAt: new Date(),
          whatsappE164: null,
          whatsappOptInAt: null,
        },
      }),
    ]);
  }
}

export const authService = new AuthService();
