import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CREDITDESK_ENV, CustomersApi, LOCAL_ENVIRONMENT, Session } from '@creditdesk/shared';
import { of } from 'rxjs';
import { CustomerList } from './customer-list';

describe('CustomerList', () => {
  const search = vi.fn(() =>
    of({
      content: [
        {
          id: 'c1',
          type: 'PF',
          document: '***.094.389-**',
          name: 'Maria Souza',
          email: 'm@example.com',
          monthlyRevenue: 4200,
          createdAt: '',
          updatedAt: '',
          version: 0,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    }),
  );

  async function render(roles: string[]) {
    await TestBed.configureTestingModule({
      imports: [CustomerList],
      providers: [
        provideRouter([]),
        { provide: CREDITDESK_ENV, useValue: LOCAL_ENVIRONMENT },
        { provide: CustomersApi, useValue: { search } },
        { provide: Session, useValue: { can: (r: string) => roles.includes(r) } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(CustomerList);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('lists customers with the masked document', async () => {
    const el = await render(['ANALYST']);
    expect(el.querySelector('tbody')?.textContent).toContain('Maria Souza');
    expect(el.querySelector('tbody')?.textContent).toContain('***.094.389-**');
    expect(search).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 20 }));
  });

  it('hides "New customer" from a read-only auditor', async () => {
    const el = await render(['AUDITOR']);
    expect(el.querySelector('a.btn-primary')).toBeNull();
  });
});
