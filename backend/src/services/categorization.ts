import { CATEGORIES, type Category } from '../llm/schemas.js';
import { llmService } from '../llm/index.js';

interface CategoryRule {
  category: Category;
  keywords: string[];
  patterns?: RegExp[];
}

// Rule-based categorization for common Indian merchants
const CATEGORY_RULES: CategoryRule[] = [
  {
    category: 'FOOD_DINING',
    keywords: [
      'swiggy',
      'zomato',
      'dominos',
      'pizza hut',
      'mcdonalds',
      'kfc',
      'burger king',
      'starbucks',
      'cafe coffee day',
      'ccd',
      'subway',
      'haldirams',
      'restaurant',
      'cafe',
      'food',
      'dining',
      'biryani',
      'kitchen',
    ],
  },
  {
    category: 'GROCERIES',
    keywords: [
      'bigbasket',
      'big basket',
      'grofers',
      'blinkit',
      'zepto',
      'dmart',
      'd-mart',
      'reliance fresh',
      'reliance smart',
      'more supermarket',
      'star bazaar',
      'spencer',
      'nature basket',
      'jiomart',
      'kirana',
      'grocery',
      'supermarket',
      'vegetables',
      'fruits',
    ],
  },
  {
    category: 'TRANSPORT',
    keywords: [
      'uber',
      'ola',
      'rapido',
      'metro',
      'dmrc',
      'bmtc',
      'auto',
      'rickshaw',
      'petrol',
      'diesel',
      'fuel',
      'hp',
      'bharat petroleum',
      'indian oil',
      'iocl',
      'bpcl',
      'hpcl',
      'parking',
      'fastag',
      'toll',
    ],
  },
  {
    category: 'SHOPPING',
    keywords: [
      'amazon',
      'flipkart',
      'myntra',
      'ajio',
      'nykaa',
      'meesho',
      'snapdeal',
      'croma',
      'reliance digital',
      'vijay sales',
      'decathlon',
      'lifestyle',
      'shoppers stop',
      'westside',
      'max',
      'pantaloons',
      'ikea',
      'pepperfry',
      'urban ladder',
    ],
  },
  {
    category: 'ENTERTAINMENT',
    keywords: [
      'netflix',
      'prime video',
      'hotstar',
      'disney',
      'spotify',
      'gaana',
      'wynk',
      'jiosaavn',
      'youtube premium',
      'bookmyshow',
      'pvr',
      'inox',
      'cinepolis',
      'movie',
      'cinema',
      'game',
      'gaming',
      'dream11',
      'mpl',
    ],
  },
  {
    category: 'UTILITIES',
    keywords: [
      'electricity',
      'power',
      'water',
      'gas',
      'piped gas',
      'mahanagar gas',
      'adani gas',
      'indraprastha gas',
      'broadband',
      'internet',
      'wifi',
      'jio',
      'airtel',
      'vi',
      'vodafone',
      'bsnl',
      'act fibernet',
      'tata sky',
      'dish tv',
      'mobile recharge',
      'postpaid',
      'prepaid',
    ],
  },
  {
    category: 'HEALTH',
    keywords: [
      'apollo',
      'fortis',
      'max hospital',
      'medplus',
      'netmeds',
      'pharmeasy',
      '1mg',
      'tata 1mg',
      'pharmacy',
      'medical',
      'doctor',
      'hospital',
      'clinic',
      'diagnostic',
      'lab',
      'pathology',
      'practo',
      'healthkart',
    ],
  },
  {
    category: 'EDUCATION',
    keywords: [
      'byju',
      'unacademy',
      'coursera',
      'udemy',
      'skillshare',
      'linkedin learning',
      'school',
      'college',
      'university',
      'tuition',
      'coaching',
      'exam',
      'books',
      'stationery',
    ],
  },
  {
    category: 'TRAVEL',
    keywords: [
      'makemytrip',
      'goibibo',
      'cleartrip',
      'yatra',
      'easemytrip',
      'irctc',
      'indian railways',
      'redbus',
      'abhibus',
      'oyo',
      'treebo',
      'fabhotels',
      'airbnb',
      'booking.com',
      'hotel',
      'flight',
      'train',
      'bus ticket',
    ],
  },
  {
    category: 'SUBSCRIPTION',
    keywords: [
      'subscription',
      'monthly',
      'annual',
      'renewal',
      'membership',
      'prime',
      'plus',
      'gold',
      'premium',
    ],
  },
  {
    category: 'EMI',
    keywords: [
      'emi',
      'loan',
      'installment',
      'repayment',
      'principal',
      'interest',
      'home loan',
      'car loan',
      'personal loan',
      'education loan',
    ],
  },
  {
    category: 'INSURANCE',
    keywords: [
      'insurance',
      'lic',
      'hdfc life',
      'icici prudential',
      'sbi life',
      'max life',
      'bajaj allianz',
      'tata aig',
      'premium',
      'policy',
      'health insurance',
      'term insurance',
    ],
  },
  {
    category: 'INVESTMENT',
    keywords: [
      'mutual fund',
      'sip',
      'zerodha',
      'groww',
      'upstox',
      'kite',
      'coin',
      'fd',
      'fixed deposit',
      'recurring deposit',
      'rd',
      'gold',
      'sovereign gold',
      'sgb',
      'ppf',
      'nps',
      'stock',
      'share',
      'demat',
    ],
  },
  {
    category: 'RENT',
    keywords: ['rent', 'house rent', 'pg', 'paying guest', 'accommodation'],
  },
];

interface CategorizeResult {
  category: string;
  subcategory: string | null;
  confidence: number;
  source: 'rule' | 'llm';
}

export class CategorizationService {
  /**
   * Categorize transaction using rules first, then LLM if uncertain.
   */
  async categorize(
    userId: string,
    merchant: string | null,
    payee: string | null,
    amount: number,
    direction: 'DEBIT' | 'CREDIT'
  ): Promise<CategorizeResult> {
    // Special cases for credits
    if (direction === 'CREDIT') {
      if (amount > 20000) {
        return {
          category: CATEGORIES.SALARY,
          subcategory: null,
          confidence: 0.6,
          source: 'rule',
        };
      }

      const name = (merchant || payee || '').toLowerCase();
      if (/refund|return/.test(name)) {
        return {
          category: CATEGORIES.REFUND,
          subcategory: null,
          confidence: 0.8,
          source: 'rule',
        };
      }

      if (/cashback|reward|bonus/.test(name)) {
        return {
          category: CATEGORIES.CASHBACK,
          subcategory: null,
          confidence: 0.8,
          source: 'rule',
        };
      }

      // P2P transfer
      return {
        category: CATEGORIES.TRANSFER,
        subcategory: null,
        confidence: 0.5,
        source: 'rule',
      };
    }

    // Try rule-based categorization
    const ruleResult = this.categorizeByRules(merchant, payee);
    if (ruleResult && ruleResult.confidence >= 0.7) {
      return ruleResult;
    }

    // If merchant looks like a person name (P2P transfer)
    if (this.looksLikePersonName(merchant || payee)) {
      return {
        category: CATEGORIES.TRANSFER,
        subcategory: null,
        confidence: 0.7,
        source: 'rule',
      };
    }

    // Fall back to LLM for uncertain cases
    try {
      const llmResult = await llmService.categorize(
        userId,
        merchant,
        payee,
        amount,
        direction
      );

      return {
        category: llmResult.result.category,
        subcategory: llmResult.result.subcategory,
        confidence: llmResult.result.confidence,
        source: 'llm',
      };
    } catch (error) {
      // If LLM fails, return rule result or OTHER
      return (
        ruleResult ?? {
          category: CATEGORIES.OTHER,
          subcategory: null,
          confidence: 0.3,
          source: 'rule',
        }
      );
    }
  }

  private categorizeByRules(
    merchant: string | null,
    payee: string | null
  ): CategorizeResult | null {
    const searchText = `${merchant || ''} ${payee || ''}`.toLowerCase();

    if (!searchText.trim()) {
      return null;
    }

    for (const rule of CATEGORY_RULES) {
      for (const keyword of rule.keywords) {
        if (searchText.includes(keyword)) {
          return {
            category: CATEGORIES[rule.category],
            subcategory: null,
            confidence: 0.8,
            source: 'rule',
          };
        }
      }

      if (rule.patterns) {
        for (const pattern of rule.patterns) {
          if (pattern.test(searchText)) {
            return {
              category: CATEGORIES[rule.category],
              subcategory: null,
              confidence: 0.75,
              source: 'rule',
            };
          }
        }
      }
    }

    return null;
  }

  private looksLikePersonName(name: string | null): boolean {
    if (!name) return false;

    // Check if it's a UPI ID format (person@bank)
    if (/@/.test(name)) {
      const localPart = name.split('@')[0] ?? '';
      // Common person name patterns in UPI IDs
      if (/^[a-z]+\.[a-z]+$/i.test(localPart)) return true;
      if (/^[a-z]+[0-9]*$/i.test(localPart) && localPart.length < 15)
        return true;
    }

    // Simple heuristic: short name, mostly letters
    const cleaned = name.replace(/[^a-zA-Z\s]/g, '').trim();
    const words = cleaned.split(/\s+/);

    if (words.length >= 1 && words.length <= 3) {
      const allShortWords = words.every(
        (w) => w.length >= 2 && w.length <= 15
      );
      if (allShortWords) return true;
    }

    return false;
  }
}

export const categorizationService = new CategorizationService();
