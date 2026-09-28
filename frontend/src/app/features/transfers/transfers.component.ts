import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { AccountService } from '../../core/services/account.service';
import { BeneficiaryService } from '../../core/services/beneficiary.service';
import { TransferService } from '../../core/services/transfer.service';
import { TransactionService } from '../../core/services/transaction.service';
import { Account } from '../../shared/models/account.model';
import { Beneficiary, TransferResult } from '../../shared/models/beneficiary.model';
import { TransactionHistoryComponent } from '../transactions/transaction-history.component';
import { Transaction } from '../../shared/models/transaction.model';
import { readError } from '../customers/customer-create.component';

const AMOUNT_PATTERN = /^\d+(\.\d{1,2})?$/;

@Component({
  selector: 'app-transfers',
  standalone: true,
  imports: [ReactiveFormsModule, TransactionHistoryComponent],
  template: `
    <section class="page">
      <h1>Transfers</h1>
      <p class="muted">Move money between NexaBank accounts via your beneficiaries.</p>

      @if (loading()) {
        <div class="card"><p>Loading accounts and beneficiaries…</p></div>
      } @else {
        <div class="grid">
          <div class="card">
            <h3>New transfer</h3>
            <form [formGroup]="transferForm" (ngSubmit)="submitTransfer()">
              <label>Source account
                <select formControlName="sourceAccountId">
                  <option value="">— Select —</option>
                  @for (a of accounts(); track a.id) {
                    <option [value]="a.id">{{ a.accountNumber }} · {{ a.balance }} {{ a.currency }}</option>
                  }
                </select>
              </label>
              <label>Beneficiary
                <select formControlName="beneficiaryId">
                  <option value="">— Select —</option>
                  @for (b of activeBeneficiaries(); track b.id) {
                    <option [value]="b.id">{{ b.nickname }} · {{ b.beneficiaryAccountNumber }}</option>
                  }
                </select>
              </label>
              <label>Amount (INR)
                <input type="number" formControlName="amount" min="0.01" step="0.01" placeholder="1000.00" />
              </label>
              @if (transferForm.controls.amount.touched && transferForm.controls.amount.invalid) {
                <p class="field-error">Enter an amount greater than 0 (max 2 decimals).</p>
              }
              <label>Description (optional)
                <input type="text" formControlName="description" maxlength="500" placeholder="Rent" />
              </label>
              <button type="submit" class="btn primary" [disabled]="transferForm.invalid || processing()">
                {{ processing() ? 'Sending…' : 'Send transfer' }}
              </button>
            </form>
            @if (transferError()) {
              <p class="error" role="alert">{{ transferError() }}</p>
            }
            @if (result()) {
              <div class="result" role="status">
                <h4>Transfer {{ result()!.transferReference }} completed</h4>
                <p>{{ result()!.amount }} {{ result()!.currency }} → {{ result()!.destinationAccountNumber }}</p>
                <p class="muted">Source balance: {{ result()!.sourceBalanceAfter }} {{ result()!.currency }}</p>
              </div>
            }
          </div>

          <div class="card">
            <h3>Beneficiaries</h3>
            @if (beneficiaries().length === 0) {
              <p class="muted">No beneficiaries yet — add one below.</p>
            } @else {
              <ul class="ben-list">
                @for (b of beneficiaries(); track b.id) {
                  <li>
                    <span><strong>{{ b.nickname }}</strong> · {{ b.beneficiaryAccountNumber }}</span>
                    @if (b.status === 'ACTIVE') {
                      <button
                        type="button"
                        class="btn ghost small"
                        [disabled]="disablingId() === b.id"
                        (click)="disableBeneficiary(b.id)"
                      >
                        Disable
                      </button>
                    } @else {
                      <span class="muted">disabled</span>
                    }
                  </li>
                }
              </ul>
            }
            <h4>Add beneficiary</h4>
            <form [formGroup]="beneficiaryForm" (ngSubmit)="addBeneficiary()">
              <label>Destination account ID
                <input type="number" formControlName="accountId" min="1" step="1" placeholder="e.g. 12" />
              </label>
              @if (beneficiaryForm.controls.accountId.touched && beneficiaryForm.controls.accountId.invalid) {
                <p class="field-error">Enter a valid account ID.</p>
              }
              <label>Nickname (optional)
                <input type="text" formControlName="nickname" maxlength="64" placeholder="My Savings" />
              </label>
              <button type="submit" class="btn primary" [disabled]="beneficiaryForm.invalid || savingBeneficiary()">
                {{ savingBeneficiary() ? 'Adding…' : 'Add beneficiary' }}
              </button>
            </form>
            @if (beneficiaryError()) {
              <p class="error" role="alert">{{ beneficiaryError() }}</p>
            }
            @if (beneficiaryMessage()) {
              <p class="action" role="status">{{ beneficiaryMessage() }}</p>
            }
          </div>
        </div>

        @if (recentTransfers().length > 0) {
          <div class="card history">
            <h3>Recent transfers</h3>
            <app-transaction-history [transactions]="recentTransfers()" />
          </div>
        }
      }
    </section>
  `,
  styles: [
    `
      h1 { margin: 0 0 0.25rem; }
      .muted { color: #5f6368; }
      .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 1rem; margin-top: 1rem; }
      form { display: grid; gap: 0.6rem; margin-top: 0.5rem; }
      label { display: grid; gap: 0.3rem; font-weight: 600; font-size: 0.9rem; }
      input, select { padding: 0.55rem 0.7rem; border: 1px solid #dadce0; border-radius: 8px; font-size: 0.95rem; font-weight: 400; }
      .btn { padding: 0.55rem 1rem; border-radius: 8px; font-weight: 600; font-size: 0.9rem; cursor: pointer; }
      .btn.primary { background: #174ea6; color: #fff; border: 0; }
      .btn.ghost { border: 1px solid #2f6fed; color: #2f6fed; background: #fff; }
      .btn.small { padding: 0.35rem 0.7rem; font-size: 0.8rem; }
      .btn:disabled { opacity: 0.6; cursor: not-allowed; }
      .field-error { color: #a50e0e; font-size: 0.85rem; margin: 0; }
      .error { color: #a50e0e; }
      .action { color: #137333; }
      .result { margin-top: 0.75rem; padding: 0.75rem; border: 1px solid #ceead6; border-radius: 8px; background: #f6fef9; }
      .result h4 { margin: 0 0 0.35rem; }
      .result p { margin: 0.2rem 0; }
      .ben-list { list-style: none; margin: 0 0 0.5rem; padding: 0; display: grid; gap: 0.5rem; }
      .ben-list li { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; padding: 0.5rem 0; border-bottom: 1px solid #e8eaed; }
      h4 { margin: 1rem 0 0.25rem; }
      .history { margin-top: 1rem; overflow-x: auto; }
    `,
  ],
})
export class TransfersComponent implements OnInit {
  private readonly accountsApi = inject(AccountService);
  private readonly beneficiariesApi = inject(BeneficiaryService);
  private readonly transfersApi = inject(TransferService);
  private readonly transactionsApi = inject(TransactionService);

  readonly accounts = signal<Account[]>([]);
  readonly beneficiaries = signal<Beneficiary[]>([]);
  readonly recentTransfers = signal<Transaction[]>([]);
  readonly loading = signal(true);
  readonly processing = signal(false);
  readonly savingBeneficiary = signal(false);
  readonly disablingId = signal<number | null>(null);
  readonly result = signal<TransferResult | null>(null);
  readonly transferError = signal<string | null>(null);
  readonly beneficiaryError = signal<string | null>(null);
  readonly beneficiaryMessage = signal<string | null>(null);

  readonly transferForm = new FormGroup({
    sourceAccountId: new FormControl<number | null>(null, [Validators.required]),
    beneficiaryId: new FormControl<number | null>(null, [Validators.required]),
    amount: new FormControl<string | null>(null, [
      Validators.required,
      Validators.min(0.01),
      Validators.pattern(AMOUNT_PATTERN),
    ]),
    description: new FormControl<string | null>(null, [Validators.maxLength(500)]),
  });

  readonly beneficiaryForm = new FormGroup({
    accountId: new FormControl<number | null>(null, [Validators.required, Validators.min(1)]),
    nickname: new FormControl<string | null>(null, [Validators.maxLength(64)]),
  });

  readonly activeBeneficiaries = computed(() =>
    this.beneficiaries().filter((b) => b.status === 'ACTIVE'),
  );

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    // Transfers act on the demo customer's own accounts — the acting customer
    // chosen in Admin, where the JWT-derived ownership used to come from.
    this.accountsApi.getMyAccounts().subscribe((accounts) => this.accounts.set(accounts));
    this.beneficiariesApi.list().subscribe({
      next: (list) => {
        this.beneficiaries.set(list);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.beneficiaryError.set(readError(err));
        this.loading.set(false);
      },
    });
    this.transactionsApi.ownHistory(0, 20).subscribe((page) =>
      this.recentTransfers.set(
        page.content.filter(
          (t) => t.transactionType === 'TRANSFER_DEBIT' || t.transactionType === 'TRANSFER_CREDIT',
        ),
      ),
    );
  }

  submitTransfer(): void {
    if (this.transferForm.invalid || this.processing()) {
      return;
    }
    this.processing.set(true);
    this.transferError.set(null);
    this.result.set(null);
    const v = this.transferForm.value;
    this.transfersApi
      .transfer(Number(v.sourceAccountId), Number(v.beneficiaryId), Number(v.amount), v.description)
      .subscribe({
        next: (r) => {
          this.result.set(r);
          this.transferForm.reset();
          this.processing.set(false);
          this.reload();
        },
        error: (err: HttpErrorResponse) => {
          this.transferError.set(readError(err));
          this.processing.set(false);
        },
      });
  }

  addBeneficiary(): void {
    if (this.beneficiaryForm.invalid || this.savingBeneficiary()) {
      return;
    }
    this.savingBeneficiary.set(true);
    this.beneficiaryError.set(null);
    this.beneficiaryMessage.set(null);
    const v = this.beneficiaryForm.value;
    this.beneficiariesApi.create(Number(v.accountId), v.nickname).subscribe({
      next: (b) => {
        this.beneficiaryMessage.set(`Beneficiary '${b.nickname}' added.`);
        this.beneficiaryForm.reset();
        this.savingBeneficiary.set(false);
        this.reload();
      },
      error: (err: HttpErrorResponse) => {
        this.beneficiaryError.set(readError(err));
        this.savingBeneficiary.set(false);
      },
    });
  }

  disableBeneficiary(id: number): void {
    this.disablingId.set(id);
    this.beneficiaryError.set(null);
    this.beneficiariesApi.disable(id).subscribe({
      next: () => {
        this.beneficiaries.update((list) =>
          list.map((b) => (b.id === id ? { ...b, status: 'DISABLED' as const } : b)),
        );
        this.disablingId.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.beneficiaryError.set(readError(err));
        this.disablingId.set(null);
      },
    });
  }
}
