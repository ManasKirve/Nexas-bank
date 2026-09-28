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

/**
 * Every screen is open in local demo mode.
 *
 * The auth/role guards and the login/register routes are gone: there is no
 * session to check, so the `data.roles` metadata that used to sit on each route
 * no longer has anything to enforce it and has been removed rather than left as
 * dead configuration. `''` goes straight to the dashboard.
 */
export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent, title: 'NexaBank — Dashboard' },
  { path: 'customers', component: CustomerListComponent, title: 'NexaBank — Customers' },
  { path: 'customers/new', component: CustomerCreateComponent, title: 'NexaBank — New customer' },
  { path: 'customers/:id', component: CustomerDetailComponent, title: 'NexaBank — Customer detail' },
  { path: 'accounts', component: AccountListComponent, title: 'NexaBank — Accounts' },
  { path: 'accounts/new', component: AccountCreateComponent, title: 'NexaBank — Open account' },
  { path: 'accounts/:id', component: AccountDetailComponent, title: 'NexaBank — Account detail' },
  { path: 'transactions', component: TransactionsComponent, title: 'NexaBank — Transactions' },
  { path: 'transfers', component: TransfersComponent, title: 'NexaBank — Transfers' },
  { path: 'fraud', component: FraudComponent, title: 'NexaBank — Fraud Monitoring' },
  { path: 'fraud/:id', component: FraudAlertDetailComponent, title: 'NexaBank — Fraud Alert Detail' },
  { path: 'admin', component: AdminComponent, title: 'NexaBank — Admin' },
  { path: '**', redirectTo: 'dashboard' },
];
