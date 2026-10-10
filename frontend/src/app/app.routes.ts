import { Routes } from '@angular/router';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { TransactionsComponent } from './features/transactions/transactions.component';
import { TransfersComponent } from './features/transfers/transfers.component';
import { FraudComponent } from './features/fraud/fraud.component';
import { FraudAlertDetailComponent } from './features/fraud/fraud-alert-detail.component';
import { AdminComponent } from './features/admin/admin.component';
import { CustomerListComponent } from './features/customers/customer-list.component';
import { CustomerCreateComponent } from './features/customers/customer-create.component';
import { CustomerDetailComponent } from './features/customers/customer-detail.component';
import { AccountListComponent } from './features/accounts/account-list.component';
import { AccountCreateComponent } from './features/accounts/account-create.component';
import { AccountDetailComponent } from './features/accounts/account-detail.component';
import { LoginComponent } from './features/auth/login.component';
import { SignupComponent } from './features/auth/signup.component';
import { authGuard, roleGuard } from './core/guards/auth.guard';
import { Role } from './shared/models/auth.model';

/** Staff roles that may run customer/account management and money movement. */
const STAFF: Role[] = ['BANK_EMPLOYEE', 'ADMIN'];
/** Roles allowed to move money (fraud analysts are deliberately excluded). */
const MONEY_MOVERS: Role[] = ['CUSTOMER', 'BANK_EMPLOYEE', 'ADMIN'];
/** Roles allowed into the fraud monitoring console. */
const FRAUD_ROLES: Role[] = ['FRAUD_ANALYST', 'ADMIN'];

/**
 * Route table with authentication and role-based access control.
 *
 * `authGuard` requires a valid JWT; `roleGuard` additionally checks the
 * `data.roles` list. Both mirror the backend's `SecurityConfig`, which remains
 * the real boundary — hiding routes is convenience, not security.
 */
export const routes: Routes = [
  { path: 'login', component: LoginComponent, title: 'NexaBank — Sign in' },
  { path: 'signup', component: SignupComponent, title: 'NexaBank — Create account' },

  {
    path: 'dashboard',
    component: DashboardComponent,
    title: 'NexaBank — Dashboard',
    canActivate: [authGuard],
  },
  {
    path: 'customers',
    component: CustomerListComponent,
    title: 'NexaBank — Customers',
    canActivate: [roleGuard],
    data: { roles: STAFF },
  },
  {
    path: 'customers/new',
    component: CustomerCreateComponent,
    title: 'NexaBank — New customer',
    canActivate: [roleGuard],
    data: { roles: STAFF },
  },
  {
    path: 'customers/:id',
    component: CustomerDetailComponent,
    title: 'NexaBank — Customer detail',
    canActivate: [roleGuard],
    data: { roles: STAFF },
  },
  {
    path: 'accounts',
    component: AccountListComponent,
    title: 'NexaBank — Accounts',
    canActivate: [roleGuard],
    data: { roles: STAFF },
  },
  {
    path: 'accounts/new',
    component: AccountCreateComponent,
    title: 'NexaBank — Open account',
    canActivate: [roleGuard],
    data: { roles: STAFF },
  },
  {
    path: 'accounts/:id',
    component: AccountDetailComponent,
    title: 'NexaBank — Account detail',
    canActivate: [authGuard],
  },
  {
    path: 'transactions',
    component: TransactionsComponent,
    title: 'NexaBank — Transactions',
    canActivate: [roleGuard],
    data: { roles: MONEY_MOVERS },
  },
  {
    path: 'transfers',
    component: TransfersComponent,
    title: 'NexaBank — Transfers',
    canActivate: [roleGuard],
    data: { roles: MONEY_MOVERS },
  },
  {
    path: 'fraud',
    component: FraudComponent,
    title: 'NexaBank — Fraud Monitoring',
    canActivate: [roleGuard],
    data: { roles: FRAUD_ROLES },
  },
  {
    path: 'fraud/:id',
    component: FraudAlertDetailComponent,
    title: 'NexaBank — Fraud Alert Detail',
    canActivate: [roleGuard],
    data: { roles: FRAUD_ROLES },
  },
  {
    path: 'admin',
    component: AdminComponent,
    title: 'NexaBank — Admin',
    canActivate: [roleGuard],
    data: { roles: ['ADMIN'] },
  },

  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: '**', redirectTo: 'dashboard' },
];
