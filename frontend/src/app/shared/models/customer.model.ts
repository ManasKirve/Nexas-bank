export type CustomerStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED';

export interface Customer {
  id: number;
  customerNumber: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  status: CustomerStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCustomerRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
}

export type UpdateCustomerRequest = CreateCustomerRequest;
