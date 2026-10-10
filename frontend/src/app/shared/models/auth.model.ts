/** Authentication models mirroring the backend `com.nexabank.dto` auth payloads. */

export type Role = 'CUSTOMER' | 'BANK_EMPLOYEE' | 'FRAUD_ANALYST' | 'ADMIN';
export type UserStatus = 'ACTIVE' | 'LOCKED' | 'DISABLED';

/** Safe user projection returned by `/api/v1/auth/*` — never contains password material. */
export interface AuthUser {
  id: number;
  username: string;
  email: string;
  role: Role;
  status: UserStatus;
  customerId: number | null;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}

/**
 * Public registration payload. The backend always creates a CUSTOMER user,
 * so no role field is accepted here (privilege escalation is impossible).
 */
export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  customerId?: number | null;
}
