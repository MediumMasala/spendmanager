import axios from 'axios';
import { prisma } from '../db.js';
import { config, isDev } from '../config.js';
import type { WeeklySummaryData } from './summary.js';

const WHATSAPP_API_URL = 'https://graph.facebook.com/v18.0';

interface TemplateParameter {
  type: 'text';
  text: string;
}

interface TemplateComponent {
  type: 'body';
  parameters: TemplateParameter[];
}

interface SendTemplateRequest {
  messaging_product: 'whatsapp';
  to: string;
  type: 'template';
  template: {
    name: string;
    language: { code: string };
    components: TemplateComponent[];
  };
}

interface WhatsAppApiResponse {
  messaging_product: string;
  contacts: Array<{ input: string; wa_id: string }>;
  messages: Array<{ id: string }>;
}

export class WhatsAppService {
  private phoneNumberId: string;
  private accessToken: string;
  private templateName: string;
  private otpTemplateName: string;

  constructor() {
    this.phoneNumberId = config.WHATSAPP_PHONE_NUMBER_ID ?? '';
    this.accessToken = config.WHATSAPP_ACCESS_TOKEN ?? '';
    this.templateName = config.WHATSAPP_TEMPLATE_WEEKLY_SUMMARY;
    this.otpTemplateName = config.WHATSAPP_TEMPLATE_OTP ?? 'otp_verification';
  }

  private isConfigured(): boolean {
    return !!(this.phoneNumberId && this.accessToken);
  }

  /**
   * Send OTP via WhatsApp.
   *
   * Template: otp_verification
   * Variables:
   * {{1}} - OTP code (e.g., "123456")
   */
  async sendOtp(
    phoneNumber: string,
    otp: string
  ): Promise<{ success: boolean; messageId?: string; error?: string }> {
    if (!this.isConfigured()) {
      if (isDev) {
        console.log(`[WhatsApp OTP Dev] Would send to ${phoneNumber}: ${otp}`);
        return { success: true, messageId: 'dev-mock-id' };
      }
      return { success: false, error: 'WhatsApp not configured' };
    }

    const payload: SendTemplateRequest = {
      messaging_product: 'whatsapp',
      to: phoneNumber.replace(/^\+/, ''), // Remove + prefix
      type: 'template',
      template: {
        name: this.otpTemplateName,
        language: { code: 'en' },
        components: [
          {
            type: 'body',
            parameters: [
              { type: 'text', text: otp },
            ],
          },
        ],
      },
    };

    try {
      const response = await axios.post<WhatsAppApiResponse>(
        `${WHATSAPP_API_URL}/${this.phoneNumberId}/messages`,
        payload,
        {
          headers: {
            Authorization: `Bearer ${this.accessToken}`,
            'Content-Type': 'application/json',
          },
        }
      );

      const messageId = response.data.messages[0]?.id;
      console.log(`[WhatsApp OTP] Sent to ${phoneNumber}, messageId: ${messageId}`);

      return { success: true, messageId };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      console.error(`[WhatsApp OTP] Failed to send to ${phoneNumber}:`, errorMessage);
      return { success: false, error: errorMessage };
    }
  }

  /**
   * Send weekly summary via WhatsApp template message.
   *
   * Template: weekly_money_summary
   * Variables:
   * {{1}} - Total spent (e.g., "₹12,345")
   * {{2}} - Top category (e.g., "Food & Dining")
   * {{3}} - Week range (e.g., "Dec 30 - Jan 5")
   * {{4}} - Transaction count (e.g., "42")
   */
  async sendWeeklySummary(
    userId: string,
    whatsappNumber: string,
    summary: WeeklySummaryData
  ): Promise<{ success: boolean; messageId?: string; error?: string }> {
    if (!this.isConfigured()) {
      if (isDev) {
        console.log(`[WhatsApp Dev] Would send to ${whatsappNumber}:`);
        console.log(`  Total spent: ₹${summary.totals.totalSpent}`);
        console.log(`  Top category: ${summary.categories[0]?.category ?? 'N/A'}`);
        return { success: true, messageId: 'dev-mock-id' };
      }
      return { success: false, error: 'WhatsApp not configured' };
    }

    // Format template variables
    const totalSpent = this.formatAmount(summary.totals.totalSpent);
    const topCategory = summary.categories[0]?.category ?? 'Other';
    const weekRange = this.formatWeekRange(
      summary.weekStart,
      summary.weekEnd
    );
    const txCount = summary.transactionCount.toString();

    const payload: SendTemplateRequest = {
      messaging_product: 'whatsapp',
      to: whatsappNumber.replace(/^\+/, ''), // Remove + prefix
      type: 'template',
      template: {
        name: this.templateName,
        language: { code: 'en' },
        components: [
          {
            type: 'body',
            parameters: [
              { type: 'text', text: totalSpent },
              { type: 'text', text: topCategory },
              { type: 'text', text: weekRange },
              { type: 'text', text: txCount },
            ],
          },
        ],
      },
    };

    // Find summary record
    const summaryRecord = await prisma.weeklySummary.findFirst({
      where: {
        userId,
        weekStart: new Date(summary.weekStart),
      },
    });

    // Create log entry
    const logEntry = await prisma.whatsappSendLog.create({
      data: {
        userId,
        summaryId: summaryRecord?.id,
        templateName: this.templateName,
        payloadJson: payload as any,
        status: 'PENDING',
      },
    });

    try {
      const response = await axios.post<WhatsAppApiResponse>(
        `${WHATSAPP_API_URL}/${this.phoneNumberId}/messages`,
        payload,
        {
          headers: {
            Authorization: `Bearer ${this.accessToken}`,
            'Content-Type': 'application/json',
          },
        }
      );

      const messageId = response.data.messages[0]?.id;

      // Update log
      await prisma.whatsappSendLog.update({
        where: { id: logEntry.id },
        data: {
          status: 'SENT',
          waMessageId: messageId,
          sentAt: new Date(),
        },
      });

      return { success: true, messageId };
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Unknown error';

      // Update log with failure
      await prisma.whatsappSendLog.update({
        where: { id: logEntry.id },
        data: {
          status: 'FAILED',
          errorMessage,
        },
      });

      return { success: false, error: errorMessage };
    }
  }

  /**
   * Handle webhook status updates.
   */
  async handleStatusUpdate(
    messageId: string,
    status: 'delivered' | 'read' | 'failed'
  ): Promise<void> {
    const statusMap = {
      delivered: 'DELIVERED',
      read: 'READ',
      failed: 'FAILED',
    } as const;

    await prisma.whatsappSendLog.updateMany({
      where: { waMessageId: messageId },
      data: { status: statusMap[status] },
    });
  }

  /**
   * Verify webhook challenge.
   */
  verifyWebhook(
    mode: string,
    token: string,
    challenge: string
  ): string | null {
    if (
      mode === 'subscribe' &&
      token === config.WHATSAPP_VERIFY_TOKEN
    ) {
      return challenge;
    }
    return null;
  }

  private formatAmount(amount: number): string {
    return `₹${amount.toLocaleString('en-IN', {
      maximumFractionDigits: 0,
    })}`;
  }

  private formatWeekRange(startIso: string, endIso: string): string {
    const start = new Date(startIso);
    const end = new Date(endIso);

    const options: Intl.DateTimeFormatOptions = {
      month: 'short',
      day: 'numeric',
    };

    const startStr = start.toLocaleDateString('en-IN', options);
    const endStr = end.toLocaleDateString('en-IN', options);

    return `${startStr} - ${endStr}`;
  }
}

export const whatsappService = new WhatsAppService();
