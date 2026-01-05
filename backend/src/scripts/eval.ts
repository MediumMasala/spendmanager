/**
 * Evaluation script for LLM transaction parsing.
 *
 * Usage:
 *   npm run eval -- --provider=mock
 *   npm run eval -- --provider=openai
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

interface GoldenEvent {
  id: string;
  text: string;
  app_source: string;
  posted_at: string;
}

interface ExpectedTransaction {
  id: string;
  isTransaction: boolean;
  amount?: number;
  direction?: string;
  merchant?: string;
  payee?: string;
  instrument?: string;
  category?: string;
  reason?: string;
}

interface EvalResult {
  id: string;
  passed: boolean;
  expected: ExpectedTransaction;
  actual: any;
  errors: string[];
}

async function loadGoldenSet(): Promise<{
  events: GoldenEvent[];
  expected: ExpectedTransaction[];
}> {
  const eventsPath = path.join(__dirname, '../../../eval/golden_events.json');
  const expectedPath = path.join(__dirname, '../../../eval/expected_transactions.json');

  const events = JSON.parse(fs.readFileSync(eventsPath, 'utf-8'));
  const expected = JSON.parse(fs.readFileSync(expectedPath, 'utf-8'));

  return { events, expected };
}

function compareResults(expected: ExpectedTransaction, actual: any): string[] {
  const errors: string[] = [];

  // Check isTransaction
  if (expected.isTransaction !== actual.isTransaction) {
    errors.push(`isTransaction: expected ${expected.isTransaction}, got ${actual.isTransaction}`);
    return errors; // If this is wrong, other checks don't make sense
  }

  if (!expected.isTransaction) {
    return errors; // Non-transaction, nothing else to check
  }

  // Check amount (allow 1% tolerance)
  if (expected.amount !== undefined) {
    const tolerance = expected.amount * 0.01;
    if (Math.abs((actual.amount || 0) - expected.amount) > tolerance) {
      errors.push(`amount: expected ${expected.amount}, got ${actual.amount}`);
    }
  }

  // Check direction
  if (expected.direction && expected.direction !== actual.direction) {
    errors.push(`direction: expected ${expected.direction}, got ${actual.direction}`);
  }

  // Check merchant (fuzzy match)
  if (expected.merchant) {
    const expectedLower = expected.merchant.toLowerCase();
    const actualLower = (actual.merchant || '').toLowerCase();
    if (!actualLower.includes(expectedLower) && !expectedLower.includes(actualLower)) {
      errors.push(`merchant: expected "${expected.merchant}", got "${actual.merchant}"`);
    }
  }

  // Check instrument
  if (expected.instrument && expected.instrument !== actual.instrument) {
    errors.push(`instrument: expected ${expected.instrument}, got ${actual.instrument}`);
  }

  return errors;
}

async function runEval(providerName: string) {
  console.log(`\nRunning evaluation with provider: ${providerName}\n`);
  console.log('='.repeat(60));

  // Dynamic import to handle ESM
  const { llmService } = await import('../llm/index.js');

  const { events, expected } = await loadGoldenSet();
  const results: EvalResult[] = [];

  let passed = 0;
  let failed = 0;
  let totalTokens = 0;

  for (let i = 0; i < events.length; i++) {
    const event = events[i]!;
    const expectedTx = expected.find(e => e.id === event.id);

    if (!expectedTx) {
      console.log(`⚠️  No expected result for ${event.id}`);
      continue;
    }

    try {
      const result = await llmService.parseTransaction(
        'eval-user',
        event.text,
        {
          appSource: event.app_source,
          locale: 'en-IN',
          timezone: 'Asia/Kolkata',
          postedAt: event.posted_at,
        },
        providerName as any
      );

      const errors = compareResults(expectedTx, result.transaction);
      const isPassed = errors.length === 0;

      results.push({
        id: event.id,
        passed: isPassed,
        expected: expectedTx,
        actual: result.transaction,
        errors,
      });

      if (isPassed) {
        passed++;
        console.log(`✅ ${event.id} - PASSED (source: ${result.source})`);
      } else {
        failed++;
        console.log(`❌ ${event.id} - FAILED`);
        errors.forEach(err => console.log(`   ${err}`));
      }

      totalTokens += result.usage.inputTokens + result.usage.outputTokens;
    } catch (error) {
      failed++;
      console.log(`❌ ${event.id} - ERROR: ${error}`);
      results.push({
        id: event.id,
        passed: false,
        expected: expectedTx,
        actual: null,
        errors: [`Parse error: ${error}`],
      });
    }
  }

  console.log('\n' + '='.repeat(60));
  console.log('\nSUMMARY');
  console.log('='.repeat(60));
  console.log(`Total: ${events.length}`);
  console.log(`Passed: ${passed} (${((passed / events.length) * 100).toFixed(1)}%)`);
  console.log(`Failed: ${failed} (${((failed / events.length) * 100).toFixed(1)}%)`);
  console.log(`Total tokens: ${totalTokens}`);

  // Calculate detailed metrics
  const transactionTests = results.filter(r => r.expected.isTransaction);
  const nonTransactionTests = results.filter(r => !r.expected.isTransaction);

  console.log(`\nTransaction detection:`);
  console.log(`  True positives: ${transactionTests.filter(r => r.actual?.isTransaction).length}`);
  console.log(`  False negatives: ${transactionTests.filter(r => !r.actual?.isTransaction).length}`);
  console.log(`  True negatives: ${nonTransactionTests.filter(r => !r.actual?.isTransaction).length}`);
  console.log(`  False positives: ${nonTransactionTests.filter(r => r.actual?.isTransaction).length}`);

  return results;
}

// Parse CLI arguments
const args = process.argv.slice(2);
const providerArg = args.find(a => a.startsWith('--provider='));
const provider = providerArg?.split('=')[1] || 'mock';

runEval(provider).catch(console.error);
