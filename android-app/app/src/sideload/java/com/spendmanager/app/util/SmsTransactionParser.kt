package com.spendmanager.app.util

/**
 * Utility to identify and parse transaction SMS messages.
 *
 * Identifies SMS from:
 * - Bank sender IDs (e.g., AD-HDFCBK, VM-ICICIB, etc.)
 * - UPI apps (e.g., PAYTMB, PHONEPE, etc.)
 * - Credit card alerts
 */
object SmsTransactionParser {

    // Known bank sender ID patterns (Indian banks)
    private val BANK_SENDER_PATTERNS = listOf(
        // Format: XX-BANKCODE or XY-BANKCODE
        Regex("""(?i)^[A-Z]{2}-HDFC"""),      // HDFC Bank
        Regex("""(?i)^[A-Z]{2}-ICICI"""),     // ICICI Bank
        Regex("""(?i)^[A-Z]{2}-SBI"""),       // SBI
        Regex("""(?i)^[A-Z]{2}-AXIS"""),      // Axis Bank
        Regex("""(?i)^[A-Z]{2}-KOTAK"""),     // Kotak
        Regex("""(?i)^[A-Z]{2}-IDBI"""),      // IDBI
        Regex("""(?i)^[A-Z]{2}-PNB"""),       // PNB
        Regex("""(?i)^[A-Z]{2}-BOB"""),       // Bank of Baroda
        Regex("""(?i)^[A-Z]{2}-CANBNK"""),    // Canara Bank
        Regex("""(?i)^[A-Z]{2}-UNIONB"""),    // Union Bank
        Regex("""(?i)^[A-Z]{2}-INDBNK"""),    // Indian Bank
        Regex("""(?i)^[A-Z]{2}-CENTBK"""),    // Central Bank
        Regex("""(?i)^[A-Z]{2}-BARODAB"""),   // Bank of Baroda (alternate)
        Regex("""(?i)^[A-Z]{2}-YESBNK"""),    // Yes Bank
        Regex("""(?i)^[A-Z]{2}-FEDBK"""),     // Federal Bank
        Regex("""(?i)^[A-Z]{2}-IDFCFB"""),    // IDFC First Bank
        Regex("""(?i)^[A-Z]{2}-RBLBNK"""),    // RBL Bank
        Regex("""(?i)^[A-Z]{2}-INDUS"""),     // IndusInd Bank
        Regex("""(?i)^[A-Z]{2}-CITI"""),      // Citibank
        Regex("""(?i)^[A-Z]{2}-HSBC"""),      // HSBC
        Regex("""(?i)^[A-Z]{2}-STANC"""),     // Standard Chartered
        Regex("""(?i)^[A-Z]{2}-AMEX"""),      // American Express
        Regex("""(?i)^[A-Z]{2}-DENA"""),      // Dena Bank
        Regex("""(?i)^[A-Z]{2}-VIJAYA"""),    // Vijaya Bank
    )

    // UPI and wallet sender patterns
    private val UPI_SENDER_PATTERNS = listOf(
        Regex("""(?i)^[A-Z]{2}-PAYTM"""),     // PayTM
        Regex("""(?i)^[A-Z]{2}-PYTM"""),      // PayTM (alternate)
        Regex("""(?i)^[A-Z]{2}-PHONPE"""),    // PhonePe
        Regex("""(?i)^[A-Z]{2}-PHONEPE"""),   // PhonePe (alternate)
        Regex("""(?i)^[A-Z]{2}-GPAY"""),      // Google Pay
        Regex("""(?i)^[A-Z]{2}-BHIM"""),      // BHIM
        Regex("""(?i)^[A-Z]{2}-AMAZONP"""),   // Amazon Pay
        Regex("""(?i)^[A-Z]{2}-MOBIKW"""),    // MobiKwik
        Regex("""(?i)^[A-Z]{2}-FREECRG"""),   // Freecharge
        Regex("""(?i)^[A-Z]{2}-AIRTEL"""),    // Airtel Payments
        Regex("""(?i)^[A-Z]{2}-JIOPAY"""),    // JioPay
        Regex("""(?i)^[A-Z]{2}-CREDCL"""),    // CRED
    )

    // Combined patterns for quick check
    private val ALL_SENDER_PATTERNS = BANK_SENDER_PATTERNS + UPI_SENDER_PATTERNS

    // Transaction keywords in message body
    private val TRANSACTION_KEYWORDS = listOf(
        "debited", "credited", "transferred", "sent", "received",
        "paid", "payment", "purchase", "withdrawn", "deposited",
        "txn", "transaction", "upi", "neft", "imps", "rtgs",
        "atm", "pos", "refund", "cashback", "balance"
    )

    // Amount pattern for Indian Rupees
    private val AMOUNT_PATTERN = Regex(
        """(?i)(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{1,2})?"""
    )

    /**
     * Check if this SMS is likely a transaction message.
     *
     * @param sender The sender ID (e.g., "AD-HDFCBK")
     * @param messageText The SMS body
     * @return true if this appears to be a transaction SMS
     */
    fun isTransactionSms(sender: String, messageText: String): Boolean {
        // Check 1: Is sender a known financial institution?
        val isKnownSender = ALL_SENDER_PATTERNS.any { it.containsMatchIn(sender) }

        if (!isKnownSender) {
            // For unknown senders, be more strict
            return isStrictTransactionMessage(messageText)
        }

        // Check 2: Does message contain amount?
        if (!AMOUNT_PATTERN.containsMatchIn(messageText)) {
            return false
        }

        // Check 3: Does message contain transaction keywords?
        val lowerMessage = messageText.lowercase()
        return TRANSACTION_KEYWORDS.any { lowerMessage.contains(it) }
    }

    /**
     * Strict check for messages from unknown senders.
     * Requires multiple indicators to reduce false positives.
     */
    private fun isStrictTransactionMessage(messageText: String): Boolean {
        val lowerMessage = messageText.lowercase()

        // Must have amount
        if (!AMOUNT_PATTERN.containsMatchIn(messageText)) {
            return false
        }

        // Must have at least 2 transaction keywords
        val keywordCount = TRANSACTION_KEYWORDS.count { lowerMessage.contains(it) }
        if (keywordCount < 2) {
            return false
        }

        // Must have account-like pattern (A/c, Acct, account)
        val hasAccountReference = lowerMessage.contains("a/c") ||
                lowerMessage.contains("acct") ||
                lowerMessage.contains("account") ||
                lowerMessage.contains("card")

        return hasAccountReference
    }

    /**
     * Get a normalized sender type for categorization.
     */
    fun getSenderType(sender: String): String {
        val upperSender = sender.uppercase()

        return when {
            // Banks
            upperSender.contains("HDFC") -> "sms_hdfc"
            upperSender.contains("ICICI") -> "sms_icici"
            upperSender.contains("SBI") -> "sms_sbi"
            upperSender.contains("AXIS") -> "sms_axis"
            upperSender.contains("KOTAK") -> "sms_kotak"
            upperSender.contains("PNB") -> "sms_pnb"
            upperSender.contains("BOB") || upperSender.contains("BARODA") -> "sms_bob"
            upperSender.contains("CANBNK") || upperSender.contains("CANARA") -> "sms_canara"
            upperSender.contains("YES") -> "sms_yesbank"
            upperSender.contains("IDFC") -> "sms_idfc"
            upperSender.contains("RBL") -> "sms_rbl"
            upperSender.contains("INDUS") -> "sms_indusind"
            upperSender.contains("FED") -> "sms_federal"
            upperSender.contains("CITI") -> "sms_citi"
            upperSender.contains("HSBC") -> "sms_hsbc"
            upperSender.contains("STANC") -> "sms_sc"
            upperSender.contains("AMEX") -> "sms_amex"

            // UPI/Wallets
            upperSender.contains("PAYTM") || upperSender.contains("PYTM") -> "sms_paytm"
            upperSender.contains("PHONPE") || upperSender.contains("PHONEPE") -> "sms_phonepe"
            upperSender.contains("GPAY") -> "sms_gpay"
            upperSender.contains("BHIM") -> "sms_bhim"
            upperSender.contains("AMAZON") -> "sms_amazonpay"
            upperSender.contains("CRED") -> "sms_cred"
            upperSender.contains("MOBIKW") -> "sms_mobikwik"
            upperSender.contains("AIRTEL") -> "sms_airtel"
            upperSender.contains("JIO") -> "sms_jio"

            else -> "sms_other"
        }
    }

    /**
     * Extract transaction details from SMS (basic extraction).
     * Full parsing is done server-side with AI.
     */
    data class BasicTransactionInfo(
        val amount: String?,
        val type: TransactionType,
        val hasAccountInfo: Boolean
    )

    enum class TransactionType {
        DEBIT, CREDIT, UNKNOWN
    }

    fun extractBasicInfo(messageText: String): BasicTransactionInfo {
        val lowerMessage = messageText.lowercase()

        // Extract amount
        val amountMatch = AMOUNT_PATTERN.find(messageText)
        val amount = amountMatch?.value

        // Determine transaction type
        val type = when {
            lowerMessage.contains("debited") ||
            lowerMessage.contains("sent") ||
            lowerMessage.contains("paid") ||
            lowerMessage.contains("withdrawn") ||
            lowerMessage.contains("purchase") -> TransactionType.DEBIT

            lowerMessage.contains("credited") ||
            lowerMessage.contains("received") ||
            lowerMessage.contains("deposited") ||
            lowerMessage.contains("refund") ||
            lowerMessage.contains("cashback") -> TransactionType.CREDIT

            else -> TransactionType.UNKNOWN
        }

        val hasAccountInfo = lowerMessage.contains("a/c") ||
                lowerMessage.contains("acct") ||
                lowerMessage.contains("account") ||
                lowerMessage.contains("card")

        return BasicTransactionInfo(amount, type, hasAccountInfo)
    }
}
