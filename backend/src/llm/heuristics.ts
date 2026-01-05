import type { ParsedTransaction } from './schemas.js';

interface HeuristicResult {
  transaction: ParsedTransaction | null;
  confidence: number;
  reason: string;
}

// Common Indian amount patterns
const AMOUNT_PATTERNS = [
  /(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{2})?)/i,
  /(?:amount|amt)[:\s]*([\d,]+(?:\.\d{2})?)/i,
  /([\d,]+(?:\.\d{2})?)\s*(?:rs\.?|inr|₹)/i,
];

// Direction patterns
const DEBIT_PATTERNS = [
  /debited/i,
  /paid\s+(?:to|at|for)/i,
  /sent\s+(?:to|money)/i,
  /transferred\s+to/i,
  /purchase/i,
  /payment\s+(?:of|for)/i,
  /spent/i,
  /withdrawn/i,
];

const CREDIT_PATTERNS = [
  /credited/i,
  /received\s+(?:from|money)/i,
  /refund/i,
  /cashback/i,
  /transferred\s+from/i,
  /deposited/i,
];

// Exclusion patterns (not transactions)
const NON_TRANSACTION_PATTERNS = [
  /\botp\b/i,
  /one.?time.?password/i,
  /verification.?code/i,
  /\blogin\b/i,
  /\balert\b.*\bsecurity\b/i,
  /password.?(?:change|reset)/i,
  /promotional/i,
  /\boffer\b.*\bexpir/i,
  /balance.?(?:is|enquiry|check)/i,
  /minimum.?balance/i,
  /kyc/i,
  /click.?(?:here|link)/i,
];

// UPI ID pattern
const UPI_ID_PATTERN = /([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)/;

// Reference ID patterns
const REF_PATTERNS = [
  /(?:ref(?:erence)?|txn|utr|rrn)[:\s#]*([A-Z0-9]{8,})/i,
  /(?:transaction\s*(?:id|no)?)[:\s#]*([A-Z0-9]{8,})/i,
];

// Bank hints
const BANKS = [
  'hdfc',
  'icici',
  'sbi',
  'axis',
  'kotak',
  'yes bank',
  'pnb',
  'bob',
  'canara',
  'union',
  'idbi',
  'indusind',
  'rbl',
  'federal',
  'bandhan',
];

// App hints
const APPS: Record<string, string> = {
  gpay: 'Google Pay',
  'google pay': 'Google Pay',
  phonepe: 'PhonePe',
  paytm: 'Paytm',
  'amazon pay': 'Amazon Pay',
  bhim: 'BHIM',
  cred: 'CRED',
};

export function parseWithHeuristics(
  text: string,
  appSource: string,
  postedAt: string
): HeuristicResult {
  const lowerText = text.toLowerCase();

  // Check if this is NOT a transaction
  for (const pattern of NON_TRANSACTION_PATTERNS) {
    if (pattern.test(text)) {
      return {
        transaction: {
          isTransaction: false,
          amount: null,
          currency: 'INR',
          direction: null,
          occurredAt: null,
          merchant: null,
          payee: null,
          instrument: null,
          bankHint: null,
          appHint: appSource,
          referenceId: null,
          confidence: 0.95,
          flags: [],
          reason: 'Non-transaction message detected',
        },
        confidence: 0.95,
        reason: 'Matched non-transaction pattern',
      };
    }
  }

  // Extract amount
  let amount: number | null = null;
  for (const pattern of AMOUNT_PATTERNS) {
    const match = text.match(pattern);
    if (match?.[1]) {
      amount = parseFloat(match[1].replace(/,/g, ''));
      break;
    }
  }

  // If no amount found, likely not a transaction
  if (amount === null) {
    return {
      transaction: null,
      confidence: 0.3,
      reason: 'No amount found',
    };
  }

  // Determine direction
  let direction: 'DEBIT' | 'CREDIT' | null = null;

  for (const pattern of DEBIT_PATTERNS) {
    if (pattern.test(text)) {
      direction = 'DEBIT';
      break;
    }
  }

  if (!direction) {
    for (const pattern of CREDIT_PATTERNS) {
      if (pattern.test(text)) {
        direction = 'CREDIT';
        break;
      }
    }
  }

  // If no clear direction, low confidence
  if (!direction) {
    return {
      transaction: null,
      confidence: 0.4,
      reason: 'Could not determine transaction direction',
    };
  }

  // Extract reference ID
  let referenceId: string | null = null;
  for (const pattern of REF_PATTERNS) {
    const match = text.match(pattern);
    if (match?.[1]) {
      referenceId = match[1];
      break;
    }
  }

  // Extract UPI ID (as payee/merchant hint)
  const upiMatch = text.match(UPI_ID_PATTERN);
  const upiId = upiMatch?.[1] ?? null;

  // Determine instrument
  let instrument: ParsedTransaction['instrument'] = null;
  if (upiId || /\bupi\b/i.test(text)) {
    instrument = 'UPI';
  } else if (/\bcard\b|\bdebit card\b|\bcredit card\b/i.test(text)) {
    instrument = 'CARD';
  } else if (/\bneft\b/i.test(text)) {
    instrument = 'NEFT';
  } else if (/\bimps\b/i.test(text)) {
    instrument = 'IMPS';
  } else if (/\bwallet\b/i.test(text)) {
    instrument = 'WALLET';
  }

  // Extract bank hint
  let bankHint: string | null = null;
  for (const bank of BANKS) {
    if (lowerText.includes(bank)) {
      bankHint = bank.toUpperCase();
      break;
    }
  }

  // Extract app hint
  let appHint: string | null = appSource;
  for (const [key, value] of Object.entries(APPS)) {
    if (lowerText.includes(key)) {
      appHint = value;
      break;
    }
  }

  // Extract merchant/payee (simplified)
  let merchant: string | null = null;
  let payee: string | null = null;

  // Try common patterns
  const toPatterns = [
    /(?:to|at)\s+([A-Za-z][A-Za-z0-9\s&'.]+?)(?:\s+on|\s+ref|\s+via|\.|\s*$)/i,
    /paid\s+(?:to\s+)?([A-Za-z][A-Za-z0-9\s&'.]+?)(?:\s+rs|\s+inr|\s+₹)/i,
  ];

  const fromPatterns = [
    /(?:from)\s+([A-Za-z][A-Za-z0-9\s&'.]+?)(?:\s+on|\s+ref|\s+via|\.|\s*$)/i,
    /received\s+(?:from\s+)?([A-Za-z][A-Za-z0-9\s&'.]+?)(?:\s+rs|\s+inr|\s+₹)/i,
  ];

  if (direction === 'DEBIT') {
    for (const pattern of toPatterns) {
      const match = text.match(pattern);
      if (match?.[1]) {
        merchant = match[1].trim().slice(0, 50);
        break;
      }
    }
  } else {
    for (const pattern of fromPatterns) {
      const match = text.match(pattern);
      if (match?.[1]) {
        payee = match[1].trim().slice(0, 50);
        break;
      }
    }
  }

  // Use UPI ID as fallback
  if (!merchant && !payee && upiId) {
    if (direction === 'DEBIT') {
      merchant = upiId;
    } else {
      payee = upiId;
    }
  }

  // Calculate confidence based on extracted fields
  let confidence = 0.5;
  if (amount) confidence += 0.15;
  if (direction) confidence += 0.15;
  if (referenceId) confidence += 0.1;
  if (merchant || payee) confidence += 0.05;
  if (instrument) confidence += 0.05;

  const transaction: ParsedTransaction = {
    isTransaction: true,
    amount,
    currency: 'INR',
    direction,
    occurredAt: postedAt,
    merchant,
    payee,
    instrument,
    bankHint,
    appHint,
    referenceId,
    confidence,
    flags: [],
    reason: null,
  };

  return {
    transaction,
    confidence,
    reason: 'Parsed with heuristics',
  };
}

// High confidence threshold for skipping LLM
export const HEURISTIC_HIGH_CONFIDENCE = 0.85;
