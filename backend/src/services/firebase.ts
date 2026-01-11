import admin from 'firebase-admin';
import { config } from '../config.js';

// Initialize Firebase Admin
let firebaseApp: admin.app.App | null = null;

function initializeFirebase(): admin.app.App {
  if (firebaseApp) {
    return firebaseApp;
  }

  // Check if Firebase credentials are provided via environment variable
  const firebaseCredentials = config.FIREBASE_SERVICE_ACCOUNT;

  if (firebaseCredentials) {
    try {
      const serviceAccount = JSON.parse(firebaseCredentials);
      firebaseApp = admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
        projectId: serviceAccount.project_id,
      });
      console.log('[Firebase] Initialized with service account credentials, project:', serviceAccount.project_id);
    } catch (error) {
      console.error('[Firebase] Failed to parse service account JSON:', error);
      throw new Error('Firebase service account configuration is invalid');
    }
  } else {
    console.error('[Firebase] FIREBASE_SERVICE_ACCOUNT environment variable not set');
    throw new Error('Firebase service account not configured');
  }

  return firebaseApp;
}

interface VerifyTokenResult {
  success: boolean;
  uid?: string;
  phone?: string;
  error?: string;
}

export class FirebaseService {
  private app: admin.app.App;

  constructor() {
    this.app = initializeFirebase();
  }

  /**
   * Verify a Firebase ID token and extract user info
   */
  async verifyIdToken(idToken: string): Promise<VerifyTokenResult> {
    try {
      const decodedToken = await admin.auth().verifyIdToken(idToken);

      return {
        success: true,
        uid: decodedToken.uid,
        phone: decodedToken.phone_number,
      };
    } catch (error: any) {
      console.error('[Firebase] Token verification failed:', error.message);

      let errorMessage = 'Token verification failed';
      if (error.code === 'auth/id-token-expired') {
        errorMessage = 'Token expired. Please sign in again.';
      } else if (error.code === 'auth/invalid-id-token') {
        errorMessage = 'Invalid token. Please sign in again.';
      } else if (error.code === 'auth/id-token-revoked') {
        errorMessage = 'Token revoked. Please sign in again.';
      }

      return {
        success: false,
        error: errorMessage,
      };
    }
  }

  /**
   * Get user info from Firebase by UID
   */
  async getUser(uid: string): Promise<admin.auth.UserRecord | null> {
    try {
      return await admin.auth().getUser(uid);
    } catch (error) {
      console.error('[Firebase] Failed to get user:', error);
      return null;
    }
  }
}

export const firebaseService = new FirebaseService();
