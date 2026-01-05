package com.spendmanager.app.util

/**
 * On-device redaction utility for privacy protection.
 *
 * Redacts:
 * - Account numbers (long digit sequences)
 * - Card numbers (16 digits, optionally with spaces)
 * - Phone numbers (10+ digits)
 * - UTR/Reference IDs (alphanumeric sequences)
 * - UPI IDs (optionally)
 *
 * Preserves:
 * - Amount tokens (Rs/INR/₹ + numbers)
 * - Merchant/payee names (non-numeric)
 * - Transaction type keywords
 */
object Redactor {

    // Patterns for redaction
    private val ACCOUNT_NUMBER_PATTERN = Regex(
        """(?<!\d)(\d{2})\d{6,14}(\d{2})(?!\d)"""
    )

    private val CARD_NUMBER_PATTERN = Regex(
        """(?<!\d)(\d{4})[\s-]?(\d{4})[\s-]?\d{4}[\s-]?(\d{4})(?!\d)"""
    )

    private val PHONE_NUMBER_PATTERN = Regex(
        """(?<!\d)(\d{2})\d{6}(\d{2})(?!\d)"""
    )

    private val UTR_PATTERN = Regex(
        """(?i)(?:utr|ref|txn|rrn)[:\s#]*([A-Z0-9]{4})[A-Z0-9]{4,}([A-Z0-9]{4})"""
    )

    private val LONG_NUMBER_PATTERN = Regex(
        """(?<!\d)\d{12,}(?!\d)"""
    )

    // Amount patterns to preserve (don't redact)
    private val AMOUNT_PATTERN = Regex(
        """(?i)(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{2})?"""
    )

    // UPI ID pattern (optional redaction)
    private val UPI_ID_PATTERN = Regex(
        """([a-zA-Z0-9._-]+)@([a-zA-Z0-9]+)"""
    )

    /**
     * Redact sensitive information from notification text.
     *
     * @param text Original notification text
     * @param redactUpiId Whether to redact UPI IDs (default: false, keeps for merchant identification)
     * @return Redacted text with sensitive data masked
     */
    fun redact(text: String, redactUpiId: Boolean = false): String {
        var result = text

        // First, protect amount tokens by temporarily replacing them
        val amounts = mutableListOf<Pair<String, String>>()
        AMOUNT_PATTERN.findAll(text).forEach { match ->
            val placeholder = "<<AMOUNT_${amounts.size}>>"
            amounts.add(placeholder to match.value)
        }

        amounts.forEach { (placeholder, value) ->
            result = result.replace(value, placeholder)
        }

        // Redact card numbers (most specific first)
        result = CARD_NUMBER_PATTERN.replace(result) { match ->
            val first = match.groupValues[1]
            val last = match.groupValues[3]
            "$first-XXXX-XXXX-$last"
        }

        // Redact account numbers
        result = ACCOUNT_NUMBER_PATTERN.replace(result) { match ->
            val first = match.groupValues[1]
            val last = match.groupValues[2]
            "${first}XXXXXX${last}"
        }

        // Redact phone numbers (10 digits)
        result = PHONE_NUMBER_PATTERN.replace(result) { match ->
            val first = match.groupValues[1]
            val last = match.groupValues[2]
            "${first}XXXXXX${last}"
        }

        // Redact UTR/reference IDs
        result = UTR_PATTERN.replace(result) { match ->
            val prefix = match.value.substringBefore(match.groupValues[1])
            val first = match.groupValues[1]
            val last = match.groupValues[2]
            "${prefix}${first}XXXX${last}"
        }

        // Redact any remaining long numbers (>11 digits)
        result = LONG_NUMBER_PATTERN.replace(result) { match ->
            val value = match.value
            if (value.length > 4) {
                "${value.take(2)}${"X".repeat(value.length - 4)}${value.takeLast(2)}"
            } else {
                "X".repeat(value.length)
            }
        }

        // Optionally redact UPI IDs
        if (redactUpiId) {
            result = UPI_ID_PATTERN.replace(result) { match ->
                val localPart = match.groupValues[1]
                val bank = match.groupValues[2]
                if (localPart.length > 4) {
                    "${localPart.take(2)}***@$bank"
                } else {
                    "***@$bank"
                }
            }
        }

        // Restore amount tokens
        amounts.forEach { (placeholder, value) ->
            result = result.replace(placeholder, value)
        }

        return result
    }

    /**
     * Check if text likely contains a transaction.
     */
    fun isLikelyTransaction(text: String): Boolean {
        val lowerText = text.lowercase()

        // Must have amount
        if (!AMOUNT_PATTERN.containsMatchIn(text)) {
            return false
        }

        // Must have transaction indicator
        val transactionIndicators = listOf(
            "debited", "credited", "paid", "received",
            "sent", "transferred", "payment", "purchase",
            "withdrawn", "deposited", "refund", "cashback"
        )

        return transactionIndicators.any { lowerText.contains(it) }
    }

    /**
     * Extract app source hint from package name.
     */
    fun getAppSourceFromPackage(packageName: String): String {
        return when {
            packageName.contains("gpay") || packageName.contains("google.android.apps.nbu") -> "gpay"
            packageName.contains("phonepe") -> "phonepe"
            packageName.contains("paytm") -> "paytm"
            packageName.contains("amazonpay") || packageName.contains("amazon.mShop") -> "amazonpay"
            packageName.contains("hdfc") -> "hdfc"
            packageName.contains("icici") -> "icici"
            packageName.contains("sbi") -> "sbi"
            packageName.contains("axis") -> "axis"
            packageName.contains("kotak") -> "kotak"
            packageName.contains("cred") -> "cred"
            packageName.contains("bhim") -> "bhim"
            else -> packageName.substringAfterLast(".").take(20)
        }
    }
}
